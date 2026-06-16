package ru.dzho.vkbot.util;

import org.springframework.web.util.UriComponentsBuilder;

public final class WriteLinkBuilder {

    private WriteLinkBuilder() {
    }

    public static String build(String base, String ref) {
        String normalizedBase = base.startsWith("http://") || base.startsWith("https://")
                ? base
                : "https://" + base;

        return UriComponentsBuilder.fromUriString(normalizedBase)
                .queryParam("ref", ref)
                .build(true)
                .toUriString();
    }
}

