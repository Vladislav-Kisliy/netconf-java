package net.juniper.netconf;

import java.util.List;
import java.util.Objects;

/**
 * Created by Vladislav Kisliy<vkisliy@productengine.com> on 04.04.18.
 */
public class DeviceSettings {

    private final String hostName;
    private final String userName;
    private final int port;
    private final int timeout;

    public DeviceSettings(String hostName, String userName, int port, int timeout) {
        this.hostName = Objects.requireNonNull(hostName);
        this.userName = Objects.requireNonNull(userName);
        this.port = port;
        this.timeout = timeout;
    }


    public String getHostName() {
        return hostName;
    }

    public String getUserName() {
        return userName;
    }

    public int getPort() {
        return port;
    }

    public int getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "DeviceSettings{" +
                "hostName='" + hostName + '\'' +
                ", userName='" + userName + '\'' +
                ", port=" + port +
                ", timeout=" + timeout +
                '}';
    }
}
