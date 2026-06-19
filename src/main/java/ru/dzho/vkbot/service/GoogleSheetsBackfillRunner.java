package ru.dzho.vkbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.dzho.vkbot.repository.ParticipantRepository;

@Component
public class GoogleSheetsBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsBackfillRunner.class);

    private final GoogleSheetsSyncService googleSheetsSyncService;
    private final ParticipantRepository participantRepository;

    public GoogleSheetsBackfillRunner(
            GoogleSheetsSyncService googleSheetsSyncService,
            ParticipantRepository participantRepository
    ) {
        this.googleSheetsSyncService = googleSheetsSyncService;
        this.participantRepository = participantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!googleSheetsSyncService.configured()) {
            return;
        }

        var participants = participantRepository.findAll();
        log.info("Starting Google Sheets backfill for {} participants", participants.size());
        participants.forEach(googleSheetsSyncService::syncParticipant);
        log.info("Google Sheets backfill finished");
    }
}
