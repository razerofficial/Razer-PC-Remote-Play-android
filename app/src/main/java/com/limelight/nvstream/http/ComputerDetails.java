package com.limelight.nvstream.http;

import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.annotation.Nullable;


public class ComputerDetails {
    public enum State {
        ONLINE, OFFLINE, UNKNOWN
    }

    public static class AddressTuple {
        public String address;
        public int port;

        public AddressTuple(String address, int port) {
            if (address == null) {
                throw new IllegalArgumentException("Address cannot be null");
            }
            if (port <= 0) {
                throw new IllegalArgumentException("Invalid port");
            }

            // If this was an escaped IPv6 address, remove the brackets
            if (address.startsWith("[") && address.endsWith("]")) {
                address = address.substring(1, address.length() - 1);
            }

            this.address = address;
            this.port = port;
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, port);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AddressTuple)) {
                return false;
            }

            AddressTuple that = (AddressTuple) obj;
            return address.equals(that.address) && port == that.port;
        }

        public String toString() {
            if (address.contains(":")) {
                // IPv6
                return "[" + address + "]:" + port;
            }
            else {
                // IPv4 and hostnames
                return address + ":" + port;
            }
        }
    }

    // Persistent attributes
    public String uuid;
    public String name;
    public AddressTuple localAddress;
    public AddressTuple remoteAddress;
    public AddressTuple manualAddress;
    public AddressTuple ipv6Address;
    public String macAddress;
    public X509Certificate serverCert;

    // Transient attributes
    public State state;
    public AddressTuple activeAddress;
    public int httpsPort;
    public int externalPort;
    public PairingManager.PairState pairState;
    public int runningGameId;
    public String rawAppList;
    public boolean nvidiaServer;
    public int offlineCount;
    public String serverVersion;
    @Nullable
    public DisplayMode activeDisplayMode = null;
    @Nullable
    public String machineIdentifier = null;
    public int serverCodecModeSupport;
    @Nullable
    public String runningGameDevice = null;
    @Nullable
    public String razerHostVersion = null;

    public ComputerDetails() {
        // Use defaults
        state = State.UNKNOWN;
    }

    public ComputerDetails(ComputerDetails details) {
        // Copy details from the other computer
        update(details);
    }

    public int guessExternalPort() {
        if (externalPort != 0) {
            return externalPort;
        }
        else if (remoteAddress != null) {
            return remoteAddress.port;
        }
        else if (activeAddress != null) {
            return activeAddress.port;
        }
        else if (ipv6Address != null) {
            return ipv6Address.port;
        }
        else if (localAddress != null) {
            return localAddress.port;
        }
        else {
            return NvHTTP.DEFAULT_HTTP_PORT;
        }
    }

    public void update(ComputerDetails details) {
        this.state = details.state;
        this.name = details.name;
        this.uuid = details.uuid;
        if (details.activeAddress != null) {
            this.activeAddress = details.activeAddress;
        }
        // We can get IPv4 loopback addresses with GS IPv6 Forwarder
        if (details.localAddress != null && !details.localAddress.address.startsWith("127.")) {
            this.localAddress = details.localAddress;
        }
        if (details.remoteAddress != null) {
            this.remoteAddress = details.remoteAddress;
        }
        else if (this.remoteAddress != null && details.externalPort != 0) {
            // If we have a remote address already (perhaps via STUN) but our updated details
            // don't have a new one (because GFE doesn't send one), propagate the external
            // port to the current remote address. We may have tried to guess it previously.
            this.remoteAddress.port = details.externalPort;
        }
        if (details.manualAddress != null) {
            this.manualAddress = details.manualAddress;
        }
        if (details.ipv6Address != null) {
            this.ipv6Address = details.ipv6Address;
        }
        if (details.macAddress != null && !details.macAddress.equals("00:00:00:00:00:00")) {
            this.macAddress = details.macAddress;
        }
        if (details.serverCert != null) {
            this.serverCert = details.serverCert;
        }
        this.externalPort = details.externalPort;
        this.httpsPort = details.httpsPort;
        this.pairState = details.pairState;
        this.runningGameId = details.runningGameId;
        this.nvidiaServer = details.nvidiaServer;
        this.rawAppList = details.rawAppList;
        this.offlineCount = details.offlineCount;
        if(details.serverVersion != null) {
            this.serverVersion = details.serverVersion;
        }
        if(details.activeDisplayMode != null) {
            this.activeDisplayMode = details.activeDisplayMode;
        }
        if(details.machineIdentifier != null && !details.machineIdentifier.isEmpty()) {
            this.machineIdentifier = details.machineIdentifier;
        }
        if(details.serverCodecModeSupport != 0) {
            this.serverCodecModeSupport = details.serverCodecModeSupport;
        }
        this.runningGameDevice = details.runningGameDevice;
        this.razerHostVersion = details.razerHostVersion;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Name: ").append(name).append(", ");
        str.append("State: ").append(state).append(", ");
        str.append("Active Address: ").append(activeAddress).append(", ");
        str.append("UUID: ").append(uuid).append(", ");
        str.append("Local Address: ").append(localAddress).append(", ");
        str.append("Remote Address: ").append(remoteAddress).append(", ");
        str.append("IPv6 Address: ").append(ipv6Address).append(", ");
        str.append("Manual Address: ").append(manualAddress).append(", ");
        str.append("MAC Address: ").append(macAddress).append(", ");
        str.append("serverCert != null: ").append(serverCert != null).append(", ");
        str.append("Pair State: ").append(pairState).append(", ");
        str.append("Is paired: ").append(ComputerDetailsExtKt.isPaired(this)).append(", ");
        str.append("Running Game ID: ").append(runningGameId).append(", ");
        str.append("HTTPS Port: ").append(httpsPort).append(", ");
        str.append("offlineCount: ").append(offlineCount).append(", ");
        str.append("serverVersion: ").append(serverVersion).append(", ");
        str.append("activeDisplayMode: ").append(activeDisplayMode).append(", ");
        str.append("machineIdentifier: ").append(machineIdentifier).append(", ");
        str.append("serverCodecModeSupport: ").append(serverCodecModeSupport).append(", ");
        str.append("runningGameDevice: ").append(runningGameDevice).append(", ");
        str.append("razerHostVersion: ").append(razerHostVersion).append(", ");
        return str.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComputerDetails that = (ComputerDetails) o;
        return httpsPort == that.httpsPort && externalPort == that.externalPort && runningGameId == that.runningGameId && nvidiaServer == that.nvidiaServer && offlineCount == that.offlineCount && Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name) && Objects.equals(localAddress, that.localAddress) && Objects.equals(remoteAddress, that.remoteAddress) && Objects.equals(manualAddress, that.manualAddress) && Objects.equals(ipv6Address, that.ipv6Address) && Objects.equals(macAddress, that.macAddress) && Objects.equals(serverCert, that.serverCert) && state == that.state && Objects.equals(activeAddress, that.activeAddress) && pairState == that.pairState && Objects.equals(runningGameDevice, that.runningGameDevice) && Objects.equals(machineIdentifier, that.machineIdentifier) && Objects.equals(serverCodecModeSupport, that.serverCodecModeSupport) && Objects.equals(activeDisplayMode, that.activeDisplayMode) && Objects.equals(razerHostVersion, that.razerHostVersion) && Objects.equals(rawAppList, that.rawAppList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, localAddress, remoteAddress, manualAddress, ipv6Address, macAddress, serverCert, state, activeAddress, httpsPort, externalPort, pairState, runningGameId, rawAppList, nvidiaServer, offlineCount, machineIdentifier, serverCodecModeSupport,  activeDisplayMode, runningGameDevice, razerHostVersion);
    }
}
