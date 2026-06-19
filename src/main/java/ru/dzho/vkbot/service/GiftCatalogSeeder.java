package ru.dzho.vkbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.repository.GiftRepository;

import java.util.List;

@Component
public class GiftCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GiftCatalogSeeder.class);

    private static final List<String> GIFT_NAMES = List.of(
            "Стикер-пак фестиваля",
            "Брелок проекта",
            "Открытка с арт-столом",
            "Мини-значок фестиваля",
            "Магнит фестиваля",
            "Сладкий комплимент",
            "Сувенирный купон",
            "Памятный браслет"
    );

    private final GiftRepository giftRepository;
    private final VkBotProperties properties;

    public GiftCatalogSeeder(GiftRepository giftRepository, VkBotProperties properties) {
        this.giftRepository = giftRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (giftRepository.countAll() > 0) {
            return;
        }

        for (int i = 1; i <= properties.seedGiftsCount(); i++) {
            String giftName = GIFT_NAMES.get((i - 1) % GIFT_NAMES.size());
            giftRepository.insert(i, giftName);
        }

        log.info("Seeded {} gifts into SQLite", properties.seedGiftsCount());
    }
}
