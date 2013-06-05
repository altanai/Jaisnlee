package com.opencloud.slee.services.sip.fmfm;

import javax.slee.Address;

public interface FMFMSubscriber {
    Address[] getAddresses();
    Address[] getBackupAddresses();
}
