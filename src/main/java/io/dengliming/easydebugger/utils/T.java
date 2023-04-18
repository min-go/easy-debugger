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
}
