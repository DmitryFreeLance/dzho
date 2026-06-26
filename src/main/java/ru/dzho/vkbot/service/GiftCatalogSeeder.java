package ru.dzho.vkbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.repository.GiftRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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

        List<GiftEntry> catalog = loadGiftCatalog();
        if (!catalog.isEmpty()) {
            for (GiftEntry entry : catalog) {
                giftRepository.insert(entry.number(), entry.name());
            }
            log.info("Seeded {} gifts into SQLite from {}", catalog.size(), properties.effectiveGiftCatalogPath());
            return;
        }

        for (int i = 1; i <= properties.seedGiftsCount(); i++) {
            String giftName = GIFT_NAMES.get((i - 1) % GIFT_NAMES.size());
            giftRepository.insert(i, giftName);
        }

        log.info("Seeded {} gifts into SQLite", properties.seedGiftsCount());
    }

    private List<GiftEntry> loadGiftCatalog() {
        String pathValue = properties.effectiveGiftCatalogPath();
        if (pathValue == null) {
            return List.of();
        }

        Path path = Path.of(pathValue);
        if (!Files.exists(path)) {
            log.warn("Gift catalog file {} does not exist, fallback seeding will be used", path);
            return List.of();
        }

        try (InputStream inputStream = Files.newInputStream(path);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 1 ? workbook.getSheetAt(1) : workbook.getSheetAt(0);
            List<GiftEntry> entries = sheet.iterator().hasNext()
                    ? sheetToEntries(sheet)
                    : List.of();
            return entries.stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load gift catalog from " + path, ex);
        }
    }

    private List<GiftEntry> sheetToEntries(Sheet sheet) {
        java.util.ArrayList<GiftEntry> entries = new java.util.ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            if (row.getCell(0) == null || row.getCell(1) == null) {
                continue;
            }
            if (row.getCell(0).getCellType() == CellType.BLANK || row.getCell(1).getCellType() == CellType.BLANK) {
                continue;
            }

            long number = extractNumber(row);
            String name = extractName(row);
            if (number <= 0 || name == null || name.isBlank()) {
                continue;
            }
            entries.add(new GiftEntry(number, name.trim()));
        }
        return entries;
    }

    private long extractNumber(Row row) {
        return switch (row.getCell(0).getCellType()) {
            case NUMERIC -> Math.round(row.getCell(0).getNumericCellValue());
            case STRING -> Long.parseLong(row.getCell(0).getStringCellValue().trim());
            default -> -1L;
        };
    }

    private String extractName(Row row) {
        return switch (row.getCell(1).getCellType()) {
            case STRING -> row.getCell(1).getStringCellValue();
            case NUMERIC -> Long.toString(Math.round(row.getCell(1).getNumericCellValue()));
            default -> null;
        };
    }

    private record GiftEntry(long number, String name) {
    }
}
