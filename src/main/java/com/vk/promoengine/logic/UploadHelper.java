package com.vk.promoengine.logic;

import com.google.common.collect.Sets;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Upload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class UploadHelper {

    private static File tempFolder = new File(System.getProperty("java.io.tmpdir"));
    private static FileOutputStream tempFileStream = null;
    private static Map<Upload, List<String>> uploaders = new HashMap<>();
    private static Set<String> allowedExtensions = Sets.newHashSet("txt");

    public static Upload createIdsUploader() {
        final List<String> lines = new ArrayList<>();
        Upload result = new Upload("Upload target users IDs list", (Upload.Receiver) (filename, mimeType) -> {
            if (StringUtils.isBlank(filename)) {
                Notification.show("Please select text file with target user ids.");
                return new NullOutputStream();
            }
            if (!allowedExtensions.contains(FilenameUtils.getExtension(filename))) {
                Notification.show("Please select TEXT file.");
                return new NullOutputStream();
            }
            try {
                tempFileStream = new FileOutputStream(new File(tempFolder, filename));
            } catch (final FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
            return tempFileStream;
        });
        result.addSucceededListener((Upload.SucceededListener) event -> {
            try {
                if (tempFileStream == null) {
                    return;
                }
                tempFileStream.close();
                File uploadedFile = new File(tempFolder, event.getFilename());
                lines.clear();
                for (String line : new HashSet<>(FileUtils.readLines(uploadedFile))) {
                    line = line.trim();
                    if (StringUtils.isNotBlank(line)) {
                        lines.add(line);
                    }
                }
                FileUtils.forceDelete(uploadedFile);
                Notification.show(lines.size() + " unique rows has been loaded.", Notification.Type.TRAY_NOTIFICATION);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        uploaders.put(result, lines);
        return result;
    }

    public static List<String> getLines(Upload uploader) {
        List<String> lines = uploaders.get(uploader);
        return lines != null ? lines : new ArrayList<>();
    }

    public static void clearUploader(Upload upload) {
        uploaders.remove(upload);
    }

}
