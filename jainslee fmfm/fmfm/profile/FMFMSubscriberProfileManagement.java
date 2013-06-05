package com.opencloud.slee.services.sip.fmfm.profile;

import javax.slee.profile.ProfileManagement;
import javax.slee.profile.ProfileVerificationException;
import javax.slee.Address;
import javax.slee.AddressPlan;

public abstract class FMFMSubscriberProfileManagement implements FMFMSubscriberProfileCMP, ProfileManagement {
    public void profileInitialize() {
        setAddresses(new Address[0]);
        setBackupAddresses(new Address[0]);
    }

    public void profileLoad() {}

    public void profileStore() {}

    /**
     * Ensure all addresses are SIP addresses
     */
    public void profileVerify() throws ProfileVerificationException {
        Address[] addresses = getAddresses();
        for (int i = 0; i < addresses.length; i++) verifyFMFMAddress(addresses[i]);
        Address[] backupAddresses = getBackupAddresses();
        for (int i = 0; i < backupAddresses.length; i++) verifyFMFMAddress(backupAddresses[i]);
    }

    private void verifyFMFMAddress(Address address) throws ProfileVerificationException {
        // Check address plan
        if (address.getAddressPlan() != AddressPlan.SIP)
            throw new ProfileVerificationException("Address \"" + address + "\" is not a SIP address");
        // Check URI scheme - must be sip: or sips:
        String uri = address.getAddressString().toLowerCase();
        if (!(uri.startsWith("sip:") || uri.startsWith("sips:")))
            throw new ProfileVerificationException("Address \"" + address + "\" is not a SIP address");            
    }
}
