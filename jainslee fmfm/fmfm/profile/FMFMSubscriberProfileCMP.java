package com.opencloud.slee.services.sip.fmfm.profile;

import javax.slee.profile.AddressProfileCMP;
import javax.slee.Address;

public interface FMFMSubscriberProfileCMP extends AddressProfileCMP {
    Address[] getBackupAddresses();
    void setBackupAddresses(Address[] backupAddresses);
}
