package com.example.securitysuite.model;

/**
 * Normalized result of an IP intelligence lookup, regardless of which
 * provider produced it. Any provider that cannot supply a given field
 * should leave it at its default (false / null / -1) rather than guessing.
 */
public final class IpLookupResult {

    private final String ip;
    private boolean vpn;
    private boolean proxy;
    private boolean hosting;
    private boolean tor;
    private String country;
    private String asn;
    private String isp;
    private String organization;
    private boolean knownAbuse;
    private final String providerName;
    private final boolean lookupFailed;

    private IpLookupResult(String ip, String providerName, boolean lookupFailed) {
        this.ip = ip;
        this.providerName = providerName;
        this.lookupFailed = lookupFailed;
    }

    public static IpLookupResult success(String ip, String providerName) {
        return new IpLookupResult(ip, providerName, false);
    }

    public static IpLookupResult failed(String ip, String providerName) {
        return new IpLookupResult(ip, providerName, true);
    }

    public String getIp() { return ip; }
    public String getProviderName() { return providerName; }
    public boolean isLookupFailed() { return lookupFailed; }

    public boolean isVpn() { return vpn; }
    public IpLookupResult setVpn(boolean vpn) { this.vpn = vpn; return this; }

    public boolean isProxy() { return proxy; }
    public IpLookupResult setProxy(boolean proxy) { this.proxy = proxy; return this; }

    public boolean isHosting() { return hosting; }
    public IpLookupResult setHosting(boolean hosting) { this.hosting = hosting; return this; }

    public boolean isTor() { return tor; }
    public IpLookupResult setTor(boolean tor) { this.tor = tor; return this; }

    public String getCountry() { return country; }
    public IpLookupResult setCountry(String country) { this.country = country; return this; }

    public String getAsn() { return asn; }
    public IpLookupResult setAsn(String asn) { this.asn = asn; return this; }

    public String getIsp() { return isp; }
    public IpLookupResult setIsp(String isp) { this.isp = isp; return this; }

    public String getOrganization() { return organization; }
    public IpLookupResult setOrganization(String organization) { this.organization = organization; return this; }

    public boolean isKnownAbuse() { return knownAbuse; }
    public IpLookupResult setKnownAbuse(boolean knownAbuse) { this.knownAbuse = knownAbuse; return this; }
}
