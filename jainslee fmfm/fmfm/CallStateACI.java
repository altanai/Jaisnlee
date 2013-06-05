package com.opencloud.slee.services.sip.fmfm;

import javax.slee.ActivityContextInterface;

public interface CallStateACI extends ActivityContextInterface {
    public String getCallID();
    public void setCallID(String id);

    public long getCallStartTime();
    public void setCallStartTime(long ms);

    public String getOriginatingAddress();
    public void setOriginatingAddress(String address);

    public String getTerminatingAddress();
    public void setTerminatingAddress(String address);

    public String getRedirectedAddress();
    public void setRedirectedAddress(String address);
}
