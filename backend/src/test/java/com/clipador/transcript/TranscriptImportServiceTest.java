package com.clipador.transcript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clipador.config.TranscriptionProperties;
import com.clipador.job.domain.ProcessingJob;
import com.clipador.storage.StorageService;
import com.clipador.transcript.domain.Transcript;
import com.clipador.video.domain.Video;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TranscriptImportServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void validatesAndPersistsSegmentsAndWordTimestamps() {
        Video video = Video.upload("video.mp4", "Video");
        ProcessingJob job = ProcessingJob.received(video, "key", "correlation");
        String json = """
                {"schemaVersion":1,"jobId":"%s","videoId":"%s","engine":"faster-whisper",
                 "modelName":"small","detectedLanguage":"pt","languageProbability":0.98,
                 "wordTimestamps":true,"durationSeconds":2.0,"durationAfterVad":1.5,
                 "fullText":"Olá mundo","segments":[
                   {"index":0,"start":0.1,"end":1.2,"text":"Olá mundo","confidence":0.9,
                    "words":[{"start":0.1,"end":0.5,"word":"Olá","probability":0.95}]}
                 ]}
                """.formatted(job.getId(), video.getId());
        StorageService storage = mock(StorageService.class);
        when(storage.open(any(), anyLong())).thenReturn(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        TranscriptRepository transcripts = mock(TranscriptRepository.class);
        TranscriptSegmentRepository segments = mock(TranscriptSegmentRepository.class);
        when(transcripts.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(transcripts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TranscriptImportService service = new TranscriptImportService(storage, transcripts, segments,
                new ObjectMapper(), new TranscriptionProperties(1024 * 1024, 100, 10));

        var result = service.importArtifact(job, "jobs/id/transcript/transcript.json");

        assertThat(result.segmentCount()).isEqualTo(1);
        assertThat(result.transcript().getDetectedLanguage()).isEqualTo("pt");
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(segments).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
