package ru.liga.greplogger.controller;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.liga.greplogger.dto.DownloadDto;
import ru.liga.greplogger.dto.GrepDto;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
public class GrepController {
    private static final String USERNAME = "user";
    private static final String PASSWORD = "password";
    private static final String HOST = "0.0.0.0";
    private static final int PORT = 22;

    @PostMapping ("/find-file")
    public String findFile(@RequestBody String fileNameForSearch) throws Exception {
        log.debug("findFile fileNameForSearch = {}", fileNameForSearch);
        String command = "find . -name " + fileNameForSearch.strip();
        Session session = null;
        ChannelExec channel = null;
        String resultPath;

        try {
            session = new JSch().getSession(USERNAME, HOST, PORT);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            channel.setOutputStream(responseStream);
            channel.connect();

            while (channel.isConnected()) {
                Thread.sleep(100);
            }

            resultPath = new String(responseStream.toByteArray());
            System.out.println(resultPath);
        } finally {
            if (session != null) {
                session.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
        }
        return resultPath;
    }

    @PostMapping("/grep")
    public String grep(@RequestBody GrepDto grepDto) throws Exception {
        log.debug("findFile searhWord = {}", grepDto);
        String command = "";
        command = getCommandForGrepByWord(grepDto, command);
        log.debug("[GrepController] grep command = {}", command);

        Session session = null;
        ChannelExec channel = null;
        String resultPath;
        try {
            session = new JSch().getSession(USERNAME, HOST, PORT);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            channel.setOutputStream(responseStream);
            channel.connect();

            while (channel.isConnected()) {
                Thread.sleep(100);
            }

            resultPath = new String(responseStream.toByteArray());
            System.out.println(resultPath);
        } finally {
            if (session != null) {
                session.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
        }
        return resultPath;
    }

    private String getCommandForGrepByWord(GrepDto grepDto, String command) {
        if (grepDto.getFileExtension().equals("log")) {
            command = "find /home/theforest/scripts/folder_source/*.log" +
                    " -amin +0 -amin -40 -exec grep -lr '" + grepDto.getSearchWord() + "' {} \\;";
        } else if (grepDto.getFileExtension().equals("log.gz")) {
            command = "find /home/theforest/scripts/folder_source/*.log.gz" +
                    " -exec grep -lr '" + grepDto.getSearchWord() + "' {} \\;";
        }
        return command;
    }

    @PostMapping("/download")
    public byte[] download(@RequestBody DownloadDto downloadDto) throws JSchException, IOException {
        byte[] resultZip = new byte[0];

        String from = "/home/theforest/scripts/folder_source/";
        String to = "C:\\Users\\aguselnikov\\IdeaProjects\\grep-logger\\src\\main\\resources\\";
        String fileName = "file.txt";

        File fileTemp = File.createTempFile("tempFile", "pdf");

        Session session = new JSch().getSession(USERNAME, HOST, PORT);
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        from = downloadDto.getFiles().get(0);
        String prefix = null;

        if (new File(to).isDirectory()) {
            prefix = to + File.separator;
        }

        // exec 'scp -f rfile' remotely
        String command = "scp -f " + from;
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();

        channel.connect();

        byte[] buf = new byte[1024];

        // send '\0'
        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();

        while (true) {
            int c = checkAck(in);
            if (c != 'C') {
                break;
            }

            // read '0644 '
            in.read(buf, 0, 5);

            long filesize = 0L;
            while (true) {
                if (in.read(buf, 0, 1) < 0) {
                    // error
                    break;
                }
                if (buf[0] == ' ') break;
                filesize = filesize * 10L + (long) (buf[0] - '0');
            }

            String file = null;
            for (int i = 0; ; i++) {
                in.read(buf, i, 1);
                if (buf[i] == (byte) 0x0a) {
                    file = new String(buf, 0, i);
                    break;
                }
            }

            System.out.println("file-size=" + filesize + ", file=" + file);

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

//            System.out.println("FileTEmp " + fileTemp.);
            // read a content of lfile
            FileOutputStream fos = new FileOutputStream(fileTemp);
            int foo;
            while (true) {
                if (buf.length < filesize) foo = buf.length;
                else foo = (int) filesize;
                foo = in.read(buf, 0, foo);
                if (foo < 0) {
                    // error
                    break;
                }
                fos.write(buf, 0, foo);
                filesize -= foo;
                if (filesize == 0L) break;
            }

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            try {
                if (fos != null) fos.close();
            } catch (Exception ex) {
                System.out.println(ex);
            }
            File zipTemp = File.createTempFile("log", "zip");
            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipTemp));
                 FileInputStream fis = new FileInputStream(fileTemp);) {
                ZipEntry entry1 = new ZipEntry("notes.txt");
                zout.putNextEntry(entry1);
                // считываем содержимое файла в массив byte
                byte[] buffer = new byte[fis.available()];
                fis.read(buffer);
                // добавляем содержимое к архиву
                zout.write(buffer);
                // закрываем текущую запись для новой записи
                zout.closeEntry();

                resultZip = Files.readAllBytes(zipTemp.toPath());

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }

        }

        channel.disconnect();
        session.disconnect();
        log.debug("resultZip = {}", resultZip.length);
        return resultZip;
    }

    @PostMapping(value = "/archiving")
    public byte[] arcving(@RequestBody DownloadDto downloadDto) throws JSchException, IOException {
        byte[] resultZip = new byte[0];

        String commandFindFile = "find . -name '" + downloadDto.getFiles() + "'";

        if (downloadDto.getFiles().size() == 1) {
            String fileName = "-name '" + downloadDto.getFiles() + "'";
        } else {
            for (String nameFile : downloadDto.getFiles()) {
                commandFindFile += "-name '" + downloadDto.getFiles() + "' -o";
            }

        }

        // tar -cf folder_target/archive.tar folder_source/*

        String from = "/home/theforest/scripts/folder_source/";
        String to = "C:\\Users\\aguselnikov\\IdeaProjects\\grep-logger\\src\\main\\resources\\";
        String fileName = "file.txt";

        File fileTemp = File.createTempFile("tempFile", "pdf");

        Session session = new JSch().getSession(USERNAME, HOST, PORT);
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        from = downloadDto.getFiles().get(0);
        String prefix = null;

        if (new File(to).isDirectory()) {
            prefix = to + File.separator;
        }

        // exec 'scp -f rfile' remotely
        String command = "scp -f " + from;
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();

        channel.connect();

        byte[] buf = new byte[1024];

        // send '\0'
        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();

        while (true) {
            int c = checkAck(in);
            if (c != 'C') {
                break;
            }

            // read '0644 '
            in.read(buf, 0, 5);

            long filesize = 0L;
            while (true) {
                if (in.read(buf, 0, 1) < 0) {
                    // error
                    break;
                }
                if (buf[0] == ' ') break;
                filesize = filesize * 10L + (long) (buf[0] - '0');
            }

            String file = null;
            for (int i = 0; ; i++) {
                in.read(buf, i, 1);
                if (buf[i] == (byte) 0x0a) {
                    file = new String(buf, 0, i);
                    break;
                }
            }

            System.out.println("file-size=" + filesize + ", file=" + file);

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

//            System.out.println("FileTEmp " + fileTemp.);
            // read a content of lfile
            FileOutputStream fos = new FileOutputStream(fileTemp);
            int foo;
            while (true) {
                if (buf.length < filesize) foo = buf.length;
                else foo = (int) filesize;
                foo = in.read(buf, 0, foo);
                if (foo < 0) {
                    // error
                    break;
                }
                fos.write(buf, 0, foo);
                filesize -= foo;
                if (filesize == 0L) break;
            }

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            try {
                if (fos != null) fos.close();
            } catch (Exception ex) {
                System.out.println(ex);
            }
            File zipTemp = File.createTempFile("log", "zip");
            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipTemp));
                 FileInputStream fis = new FileInputStream(fileTemp);) {
                ZipEntry entry1 = new ZipEntry("notes.txt");
                zout.putNextEntry(entry1);
                // считываем содержимое файла в массив byte
                byte[] buffer = new byte[fis.available()];
                fis.read(buffer);
                // добавляем содержимое к архиву
                zout.write(buffer);
                // закрываем текущую запись для новой записи
                zout.closeEntry();

                resultZip = Files.readAllBytes(zipTemp.toPath());

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }

        }

        channel.disconnect();
        session.disconnect();
        log.debug("resultZip = {}", resultZip.length);
        return resultZip;
    }


    private static void copyRemoteToLocal(Session session, String from, String to, String fileName) throws JSchException, IOException {
        from = from + File.separator + fileName;
        String prefix = null;

        if (new File(to).isDirectory()) {
            prefix = to + File.separator;
        }

        // exec 'scp -f rfile' remotely
        String command = "scp -f " + from;
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();

        channel.connect();

        byte[] buf = new byte[1024];

        // send '\0'
        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();

        while (true) {
            int c = checkAck(in);
            if (c != 'C') {
                break;
            }

            // read '0644 '
            in.read(buf, 0, 5);

            long filesize = 0L;
            while (true) {
                if (in.read(buf, 0, 1) < 0) {
                    // error
                    break;
                }
                if (buf[0] == ' ') break;
                filesize = filesize * 10L + (long) (buf[0] - '0');
            }

            String file = null;
            for (int i = 0; ; i++) {
                in.read(buf, i, 1);
                if (buf[i] == (byte) 0x0a) {
                    file = new String(buf, 0, i);
                    break;
                }
            }

            System.out.println("file-size=" + filesize + ", file=" + file);

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            // read a content of lfile
            FileOutputStream fos = new FileOutputStream(prefix == null ? to : prefix + file);
            int foo;
            while (true) {
                if (buf.length < filesize) foo = buf.length;
                else foo = (int) filesize;
                foo = in.read(buf, 0, foo);
                if (foo < 0) {
                    // error
                    break;
                }
                fos.write(buf, 0, foo);
                filesize -= foo;
                if (filesize == 0L) break;
            }

            if (checkAck(in) != 0) {
                System.exit(0);
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            try {
                if (fos != null) fos.close();
            } catch (Exception ex) {
                System.out.println(ex);
            }
        }

        channel.disconnect();
        session.disconnect();
    }

    public static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //         -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');
            if (b == 1) { // error
                System.out.print(sb.toString());
            }
            if (b == 2) { // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }
}

