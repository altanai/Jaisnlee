package com.opencloud.slee.services.sip.fmfm;

import javax.slee.Address;

public interface FMFMSubscriberLookup {
    FMFMSubscriber lookup(Address address);
}
