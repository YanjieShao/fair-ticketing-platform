package com.fairticketing.waitlist.scheduler;

import com.fairticketing.waitlist.service.WaitlistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaitlistOfferExpiryJob {

    private final WaitlistService waitlist;

    public WaitlistOfferExpiryJob(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    @Scheduled(fixedDelayString = "${ticketing.waitlist.expiry-scan-interval:PT15S}")
    public void expireOffers() {
        waitlist.expireOverdueOffers();
    }
}
