package com.clmextract.sftp;

import com.clmextract.config.AppConfig;
import com.jcraft.jsch.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SftpUploader {

    private static final Logger LOG = LogManager.getLogger(SftpUploader.class);

    private final AppConfig.SftpConfig sftpConfig;

    public SftpUploader(AppConfig.SftpConfig sftpConfig) {
        this.sftpConfig = sftpConfig;
    }

    /**
     * Uploads each file in zipParts to targetDir on the SFTP server.
     * Creates targetDir if it does not exist.
     * @throws IOException if connection or any upload fails
     */
    public void upload(List<Path> zipParts, String targetDir) throws IOException {
        if (zipParts == null || zipParts.isEmpty()) {
            LOG.info("Nothing to upload");
            return;
        }

        String host = sftpConfig.getHost();
        int port = sftpConfig.getPort();
        String username = sftpConfig.getUsername();
        String password = sftpConfig.getPassword();

        LOG.info("Connecting to SFTP {}:{} as {}", host, port, username);

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            session.connect(30_000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            tryMkdir(channel, targetDir);

            for (Path part : zipParts) {
                String remotePath = targetDir + "/" + part.getFileName();
                channel.put(Files.newInputStream(part), remotePath, ChannelSftp.OVERWRITE);
                LOG.info("Uploaded {} ({} bytes) to {}", part.getFileName(), Files.size(part), remotePath);
            }

            LOG.info("SFTP upload complete: {} file(s)", zipParts.size());
        } catch (JSchException e) {
            throw new IOException("SFTP connection error: " + e.getMessage(), e);
        } catch (SftpException e) {
            throw new IOException("SFTP transfer error: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private void tryMkdir(ChannelSftp channel, String dir) {
        try {
            channel.mkdir(dir);
            LOG.info("Created remote directory: {}", dir);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_FAILURE) {
                LOG.debug("Remote directory already exists: {}", dir);
            } else {
                LOG.warn("mkdir {} failed (status {}): {}", dir, e.id, e.getMessage());
            }
        }
    }
}
