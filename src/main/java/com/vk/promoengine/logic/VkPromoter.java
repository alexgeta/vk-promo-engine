package com.vk.promoengine.logic;

import com.company.AnticaptchaApiWrapper;
import com.company.AnticaptchaResult;
import com.company.AnticaptchaTask;
import com.google.common.collect.Sets;
import com.vdurmont.emoji.EmojiParser;
import com.vk.api.VkApi;
import com.vk.api.entitites.Message;
import com.vk.api.entitites.User;
import com.vk.api.enums.UserField;
import com.vk.api.exceptions.CaptchaNeededException;
import com.vk.api.exceptions.PermissionException;
import com.vk.api.exceptions.ValidationRequiredException;
import com.vk.api.exceptions.VkApiException;
import com.vk.api.util.VkUtil;
import com.vk.promoengine.entities.*;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.vk.promoengine.entities.PromoAction.Type.*;

public class VkPromoter implements Runnable {

    private static final Logger log = Logger.getLogger(VkPromoter.class);
    private static final String antigateApiHost = "api.anti-captcha.com";
    private static String antigateClientKey;
    private final Campaign campaign;
    private final PromoInfo promoInfo;
    private final VkApi vkApi;
    private final ApiValidator apiValidator;
    private final PromotedUsersContainer promotedUsers;
    private final TargetUsersContainer targetUsers;
    private boolean msgLimitExceeded;
    private boolean friendRequestLimitExceeded = true;
    private boolean isRunning;
    private Queue<String> welcomeMessages;
    private Queue<String> promoMessages;
    private String prevWelcomeMsg;
    private String prevPromoMsg;

    public VkPromoter(VkPromoterArgs args) throws VkApiException, IOException {
        campaign = args.getCampaign();
        promoInfo = campaign.getPromoInfo();
        welcomeMessages = new LinkedList<>(promoInfo.getWelcomeMessages());
        promoMessages = new LinkedList<>(promoInfo.getPromoMessages());
        this.vkApi = new VkApi(args.getAccessToken(), convertProxy(args.getProxy()));
        this.apiValidator = new ApiValidator(args.getProxy());
        this.antigateClientKey = args.getAntigateKey();
        this.promotedUsers = args.getPromotedUsers();
        this.targetUsers = args.getTargetUsers();
    }

    private com.vk.api.entitites.Proxy convertProxy(Proxy proxy) {
        return new com.vk.api.entitites.Proxy(
                proxy.getHost(), proxy.getPort(), proxy.getLogin(), proxy.getPassword(), proxy.getScheme());
    }


    public void stop() {
        isRunning = false;
    }

    private void doPromotion() throws VkApiException {
        int cycleCount = 0;
        while (isRunning) {
            log.debug("Begin cycle #" + (++cycleCount));
            vkApi.setOnline();
            checkAndProcessUnreadMessages();
            checkAndProcessApprovedFriendRequests();
            String onlineUserId;
            if ((!msgLimitExceeded || !friendRequestLimitExceeded)) {
                onlineUserId = findOnlineUserId();
            } else {
                waitFewSecs();
                continue;
            }
            if (onlineUserId == null) {
                if (isRunning) {
                    waitFewSecs();
                }
                continue;
            }
            if (!msgLimitExceeded && sendWelcomeMsg(onlineUserId) > 0) {
                reportPerformedPromoAction(onlineUserId, SEND_WELCOME_MSG);
                updateMessageLog(onlineUserId);
            } else if (!friendRequestLimitExceeded && sendFriendRequest(onlineUserId)) {
                reportPerformedPromoAction(onlineUserId, SEND_FRIEND_REQUEST);
            } else {
                targetUsers.add(onlineUserId);
            }
            waitFewSecs();
            log.debug("End cycle #" + cycleCount + "\n");
        }
    }

    private void updateMessageLog(String userId) {
        PromotedUser promotedUser = promotedUsers.get(userId);
        if (promotedUser == null) {
            log.debug(String.format("There are no promoted user with id %s", userId));
            return;
        }
        try {
            List<Message> messageHistory = vkApi.getMessageHistory(userId);
            List<String> messageLines = new ArrayList<>();
            Collections.reverse(messageHistory);
            for (Message message : messageHistory) {
                String messageBody = EmojiParser.removeAllEmojis(message.getBody());
                if (StringUtils.isNotBlank(messageBody)) {
                    messageLines.add(message.getFromId() + ": " + messageBody);
                }
            }
            promotedUser.setMessageLog(StringUtils.join(messageLines, "\n"));
        } catch (VkApiException e) {
            logError(String.format("Failed to update chat log userId %s", userId), e);
        }
    }

    private void logError(String message, Throwable e) {
        log.error(message, e);
    }

    private void reportPerformedPromoAction(String userId, PromoAction.Type actionType) {
        reportPerformedPromoAction(userId, actionType, null);
    }

    private void reportPerformedPromoAction(String userId, PromoAction.Type actionType, Date time) {
        if (time == null) {
            time = new Date();
        }
        PromoAction performedPromoAction = new PromoAction(actionType, time);
        PromotedUser promotedUser = promotedUsers.get(userId);
        if (promotedUser != null) {
            promotedUser.getPerformedActions().add(performedPromoAction);
        } else {
            promotedUser = new PromotedUser(userId, vkApi.getMyId(), campaign, Sets.newHashSet(performedPromoAction));
            promotedUsers.add(promotedUser);
        }
        log.debug("Action " + actionType + " performed on user '" + userId + "'.");
    }

    private boolean sendFriendRequest(String userId) throws VkApiException {
        boolean result = false;
        try {
            try {
                result = vkApi.sendFriendRequest(userId);
            } catch (CaptchaNeededException e) {
                log.debug("Captcha requested for send friend request to user '" + userId + "'.");
                String captchaValue = solveCaptcha(e.getCaptchaImgUrl());
                if (StringUtils.isBlank(captchaValue)) {
                    log.debug("WARNING: Captcha is blank");
                    return false;
                }
                log.debug("Solved captcha value: '" + captchaValue + "'");
                result = vkApi.sendFriendRequest(userId, e.getCaptchaSid(), captchaValue);
            }
        } catch (PermissionException e) {
            log.debug("FRIEND_REQUEST_LIMIT exceeded.");
            friendRequestLimitExceeded = true;
        } catch (CaptchaNeededException e) {
            log.debug("Invalid Captcha value: " + e.getMessage());
        }
        return result;
    }

    private String solveCaptcha(String captchaImgUrl) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new URL(captchaImgUrl));
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            Base64OutputStream base64Output = new Base64OutputStream(byteStream);
            ImageIO.write(bufferedImage, "jpg", base64Output);
            String base64 = new String(byteStream.toByteArray());
            base64 = base64.replace("\n", "").replace("\r", "");
            AnticaptchaTask task = AnticaptchaApiWrapper.createImageToTextTask(antigateApiHost, antigateClientKey, base64);
            while (true) {
                AnticaptchaResult taskResult = AnticaptchaApiWrapper.getTaskResult(antigateApiHost, antigateClientKey, task);
                if (taskResult.getStatus().equals(AnticaptchaResult.Status.ready)) {
                    return taskResult.getSolution();
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private void waitFewSecs() {
        if (!isRunning) return;
        int sec = getRandom(10, 15);
        log.debug("Waiting for " + sec + " seconds...");
        try {
            Thread.sleep(1000 * sec);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void checkAndProcessApprovedFriendRequests() throws VkApiException {
        List<String> notPromotedApprovedFriends = VkUtil.filterUsersByValue(UserField.IS_FRIEND, "1", getFriendRequestSentIds(), vkApi);
        log.debug("Not promoted approved friends: " + notPromotedApprovedFriends.size());
        for (String friendId : notPromotedApprovedFriends) {
            if (hasUnreadMessages(friendId)) {
                log.debug("Has unread msgs");
                continue;
            } else if (!promotedUsers.isActionPerformed(friendId, SEND_WELCOME_MSG) && sendWelcomeMsg(friendId) > 0) {
                reportPerformedPromoAction(friendId, SEND_WELCOME_MSG);
                updateMessageLog(friendId);
            } else if (sendGroupInvite(friendId)) {
                reportPerformedPromoAction(friendId, SEND_GROUP_INVITE);
            } else {
                reportPerformedPromoAction(friendId, IGNORE);
            }
            waitFewSecs();
        }
    }

    private boolean hasUnreadMessages(String friendId) throws VkApiException {
        for (Message message : vkApi.getMessageHistory(friendId)) {
            if (message.getUnread()) {
                return true;
            }
        }
        return false;
    }

    private void checkAndProcessUnreadMessages() throws VkApiException {
        List<Message> unreadMessages = vkApi.getUnreadMessages();
        unreadMessages = filterMessages(unreadMessages);
        log.debug("Unread messages: " + unreadMessages.size());
        for (Message message : unreadMessages) {
            String userId = message.getUserId().toString();
            waitFewSecs();
            if (promotedUsers.isActionPerformed(userId, SEND_PROMO_MSG)) {
                //skip already promoted user
                vkApi.markAsRead(userId);
            } else if (promotedUsers.isActionPerformed(userId, SEND_WELCOME_MSG) && sendPromoMsg(userId) > 0) {
                reportPerformedPromoAction(userId, SEND_PROMO_MSG);
                vkApi.markAsRead(userId);
            } else if (!promotedUsers.isActionPerformed(userId, SEND_WELCOME_MSG) && sendWelcomeMsg(userId) > 0) {
                reportPerformedPromoAction(userId, SEND_WELCOME_MSG);
                vkApi.markAsRead(userId);
            }
            updateMessageLog(userId);
        }
    }

    private List<Message> filterMessages(List<Message> unreadMessages) throws VkApiException {
        List<Message> result = new ArrayList<>();
        synchronized (promotedUsers) {
            for (Message message : unreadMessages) {
                String userId = message.getUserId().toString();
                if (promotedUsers.contains(userId)) {
                    result.add(message);
                }
            }
        }
        return result;
    }

    private boolean sendGroupInvite(String userId) throws VkApiException {
        if (StringUtils.isBlank(promoInfo.getGroupId())) {
            return false;
        }
        try {
            return vkApi.sendGroupInvite(promoInfo.getGroupId(), userId);
        } catch (PermissionException e) {
            log.debug("User '" + userId + "' restricted for send group invite.");
            reportPerformedPromoAction(userId, IGNORE);
        }
        return false;
    }

    private List<String> getFriendRequestSentIds() {
        List<String> result = new ArrayList<>();
        List<PromotedUser> friendRequestedUsers = promotedUsers.getPromotedByType(SEND_FRIEND_REQUEST);
        for (PromotedUser promotedUser : friendRequestedUsers) {
            if (promotedUser.getPromoterId().equals(vkApi.getMyId())) {
                result.add(promotedUser.getVkId());
            }
        }
        return result;
    }

    private String findOnlineUserId() throws VkApiException {
        String result = null;
        log.debug("Searching online user...");
        long cycleStartTime = System.currentTimeMillis();
        while (result == null && isRunning && !isTimeOut(cycleStartTime, TimeUnit.MINUTES.toMillis(1))) {
            String userId;
            try {
                userId = targetUsers.getOne();
            } catch (IndexOutOfBoundsException e) {
                log.debug("No more users in target user set!\n");
                return null;
            }
            User user;
            try {
                user = vkApi.getUser(userId);
            } catch (VkApiException e) {
                targetUsers.add(userId);
                throw e;
            }
            if (user == null) {
                log.warn("API returns \'null\' for userId '" + userId + "'!");
                targetUsers.add(userId);
                return null;
            }
            if (user.isDeactivated()) {
                continue;
            }
            if (user.getIsOnline() &&
                    (!msgLimitExceeded && user.getCanWritePrivateMsg()) || (!friendRequestLimitExceeded && user.getCanSendFriendRequest())) {
                result = user.getId();
            } else {
                targetUsers.add(userId);
            }
        }
        return result;
    }

    private boolean isTimeOut(long startTime, long duration, TimeUnit timeUnit) {
        return isTimeOut(startTime, timeUnit.toMillis(duration));
    }

    private boolean isTimeOut(long startTime, long duration) {
        return System.currentTimeMillis() > (startTime + duration);
    }

    private int sendWelcomeMsg(String userId) throws VkApiException {
        int sentMessageId = 0;
        String message = chooseRandomMsg(promoInfo.getWelcomeMessages(), prevWelcomeMsg);
//        String message = getNextMessage(welcomeMessages);
        try {
            sentMessageId = sendMessage(userId, message);
        } catch (PermissionException e) {
            log.debug("MSG_SENT_LIMIT exceeded.");
            msgLimitExceeded = true;
            waitFewSecs();
        }
        if (sentMessageId > 0) {
            prevWelcomeMsg = message;
        }
        return sentMessageId;
    }

    private int sendPromoMsg(String userId) throws VkApiException {
        int sentMessageId = 0;
        String message = chooseRandomMsg(promoInfo.getPromoMessages(), prevPromoMsg);
        try {
            sentMessageId = sendMessage(userId, message);
        } catch (PermissionException e) {
            log.error(String.format("Failed to send promo message to user %s: %s", userId, e.getMessage()));
            vkApi.markAsRead(userId);
        }
        if (sentMessageId > 0) {
            prevPromoMsg = message;
        }
        return sentMessageId;
    }

    private int sendMessage(String userId, String message) throws VkApiException {
        int messageId = 0;
        boolean validated = true;
        log.debug(String.format("Sending message '%s' to user %s ...", message, userId));
        while (validated) {
            try {
                messageId = vkApi.sendPrivateMessage(message, userId);
                break;
            } catch (ValidationRequiredException e) {
                if (!isRunning) {
                    break;
                }
                log.debug("Validating api request... " + e.getRedirectUrl());
                validated = apiValidator.validate(e.getRedirectUrl());
                log.debug("Request validated: " + validated);
            }
        }
        log.debug("Sent message id: " + messageId);
        return messageId;
    }

    private String chooseRandomMsg(List<String> messages, String previousMsg) {
        String result;
        do {
            result = messages.get(getRandom(0, messages.size() - 1));
        } while (StringUtils.equals(result, previousMsg));
        return result;
    }

    public void recover(Date from) throws VkApiException {
        log.debug("Processing recovery from " + from + " ...");
        List<Message> outputMessages = vkApi.getMessages(0, (System.currentTimeMillis() - from.getTime()) / 1000L, true);
        Set<String> welcomeMsgsSet = new HashSet<>(promoInfo.getWelcomeMessages());
        Set<String> promoMsgsSet = new HashSet<>(promoInfo.getPromoMessages());
        Set<String> affectedUsers = new HashSet<>();
        for (Message message : outputMessages) {
            String msgBody = message.getBody();
            String userId = message.getUserId().toString();
            affectedUsers.add(userId);
            if (welcomeMsgsSet.contains(msgBody) && !promotedUsers.isActionPerformed(userId, SEND_WELCOME_MSG)) {
                reportPerformedPromoAction(userId, SEND_WELCOME_MSG, message.getDate());
            } else if (promoMsgsSet.contains(msgBody) && !promotedUsers.isActionPerformed(userId, SEND_PROMO_MSG)) {
                reportPerformedPromoAction(userId, SEND_PROMO_MSG, message.getDate());
            } else {
                continue;
            }
            synchronized (targetUsers) {
                if (targetUsers.remove(userId)) {
                    log.debug("UserId '" + userId + "' was deleted from target user set.");
                }
            }
        }
        affectedUsers.forEach(this::updateMessageLog);
        log.debug("Recovery finished.");
    }

    private int getRandom(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }

    @Override
    public void run() {
        Thread.currentThread().setName(String.format("%s:%s", vkApi.getFullName(), vkApi.getMyId()));
        try {
            isRunning = true;
            log.debug("Started");
            doPromotion();
        } catch (Throwable e) {
            stop();
            log.error(e.getMessage(), e);
            log.debug("Last time request params:\n" + vkApi.getLastTimeParams().toString());
            log.debug("Last time response:\n" + vkApi.getLastTimeResponse());
        } finally {
            apiValidator.close();
            log.debug("Stopped");
        }
    }
}