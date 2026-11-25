package com.ojttrack.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's clock. Services ask it for "today" instead of calling
 * LocalDate.now(), so the timezone is explicit and tests can pass a fixed clock.
 *
 * MVP assumption: one timezone for everyone (Asia/Manila, UTC+8, no daylight-saving
 * time). Time logs store local DATE/TIME values, which is exact without DST.
 * Supporting companies in other timezones later would need (a) a zone per placement
 * or school for the "today" check and (b) TIMESTAMPTZ start/end values for logs in
 * zones with DST, plus a migration of existing rows. Changing the property below only
 * swaps the single zone; it is not multi-timezone support.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${ojttrack.time-zone:Asia/Manila}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
