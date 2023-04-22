package io.dengliming.easydebugger.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用工具类
 */
@Slf4j
public final class T {

    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");

    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2})+$");

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String format(LocalDateTime dateTime) {
        return dateTime.format(formatter);
    }

    /**
     * 判断是否合法端口
     *
     * @param port
     * @return
     */
    public static boolean isValidPort(String port) {
        if (port == null || port.length() == 0) {
            return false;
        }

        Matcher matcher = PORT_PATTERN.matcher(port);

        if (matcher.matches()) {
            int portNumber = Integer.parseInt(port);
            return portNumber > 0 && portNumber <= 65535;
        }
        return false;
    }

    /**
     * 获取当前本机ip地址（非127.0.0.1）
     *
     * @return
     */
    public static String getLocalHostIp() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    // loopback地址即本机地址，IPv4的loopback范围是127.0.0.0 ~ 127.255.255.255
                    if (ip != null && ip instanceof Inet4Address && !ip.isLoopbackAddress()
                            && ip.getHostAddress().indexOf(":") == -1) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.error("getLocalHostIp error.", e);
        }
        return null;
    }

    /**
     * 判断字符串是否为空
     *
     * @param str
     * @return
     */
    public static boolean hasLength(String str) {
        return (str != null && !str.isEmpty());
    }

    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String bytesToHex(byte[] bytes) {
        // 一个byte为8位，可用两个十六进制位标识
        char[] buf = new char[bytes.length * 2];
        int a, index = 0;
        for (byte b : bytes) { // 使用除与取余进行转换
            if (b < 0) {
                a = 256 + b;
            } else {
                a = b;
            }
            buf[index++] = HEX_CHAR[a / 16];
            buf[index++] = HEX_CHAR[a % 16];
        }
        return new String(buf);
    }

    public static byte[] toHexBytes(String str) {
        if (str == null || str.trim().equals("")) {
            return new byte[0];
        }
        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }
        return bytes;
    }

    public static String generateClientId(InetSocketAddress socketAddress) {
        String clientIp = socketAddress.getAddress().getHostAddress();
        int clientPort = socketAddress.getPort();
        return String.format("%s:%d", clientIp, clientPort);
    }

    public static boolean isHexString(String str) {
        return HEX_STRING_PATTERN.matcher(str).matches();
    }

    /**
     * 判断是否中文字符
     *
     * @param c c
     * @return boolean
     */
    public static boolean isChinese(final char c) {
        final Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否乱码.
     *
     * @param strName strName
     * @return boolean
     */
    public static boolean isMessyCode(final String strName) {
        final Pattern p = Pattern.compile("\\s*|\t*|\r*|\n*");
        final Matcher m = p.matcher(strName);
        final String after = m.replaceAll("");
        final String temp = after.replaceAll("\\p{P}", "")
                .replaceAll("`", "")
                .replaceAll("~", "")
                .replaceAll("\\$", "")
                .replaceAll("\\^", "")
                .replaceAll("\\+", "")
                .replaceAll("=", "")
                .replaceAll("<", "")
                .replaceAll(">", "")
                .replaceAll("\\|", "");
        final char[] ch = temp.trim().toCharArray();
        for (int i = 0; i < ch.length; i++) {
            final char c = ch[i];
            if (!Character.isLetterOrDigit(c)) {
                if (!isChinese(c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
