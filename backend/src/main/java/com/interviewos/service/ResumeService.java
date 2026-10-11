package com.interviewos.service;

import com.interviewos.domain.CandidateProfile;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.ProfileRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 20_000;
    private static final int MIN_TEXT_CHARS = 50;

    public record ResumeStatus(boolean uploaded, String filename, Instant updatedAt) {}

    private final ProfileRepository profiles;
    private final Clock clock;

    public ResumeService(ProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public ResumeStatus upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_RESUME", "Choose a PDF or DOCX resume to upload.");
        if (file.getSize() > MAX_FILE_BYTES) throw ApiException.badRequest("RESUME_TOO_LARGE", "Resume files must be 5 MB or smaller.");
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        if (!extension.equals("pdf") && !extension.equals("docx")) {
            throw ApiException.badRequest("UNSUPPORTED_RESUME_FORMAT", "Only PDF and DOCX resumes are supported.");
        }

        String extracted;
        try {
            byte[] bytes = file.getBytes();
            extracted = extension.equals("pdf") ? extractPdf(bytes) : extractDocx(bytes);
        } catch (IOException | RuntimeException e) {
            log.warn("Resume extraction failed ({}); rejecting upload", e.getClass().getSimpleName());
            throw ApiException.badRequest("INVALID_RESUME_FILE", "The uploaded file could not be read as a valid PDF or DOCX.");
        }

        String text = normalize(extracted);
        if (text.length() < MIN_TEXT_CHARS) {
            throw ApiException.badRequest("RESUME_TEXT_NOT_FOUND", "The file contains too little readable text. Upload a text-based PDF or DOCX.");
        }
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);

        CandidateProfile profile = profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        Instant now = clock.instant();
        profile.setResumeText(text);
        profile.setResumeFilename(filename);
        profile.setResumeUpdatedAt(now);
        profile.setUpdatedAt(now);
        return new ResumeStatus(true, filename, now);
    }

    @Transactional(readOnly = true)
    public ResumeStatus status(Long userId) {
        CandidateProfile profile = profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        return new ResumeStatus(profile.getResumeText() != null && !profile.getResumeText().isBlank(),
                profile.getResumeFilename(), profile.getResumeUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<String> textForInterview(Long userId) {
        return profiles.findByUserId(userId).map(CandidateProfile::getResumeText).filter(text -> !text.isBlank());
    }

    @Transactional
    public ResumeStatus delete(Long userId) {
        CandidateProfile profile = profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        profile.setResumeText(null);
        profile.setResumeFilename(null);
        profile.setResumeUpdatedAt(null);
        profile.setUpdatedAt(clock.instant());
        return new ResumeStatus(false, null, null);
    }

    static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    static String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
    }

    private static String safeFilename(String original) {
        String normalized = original == null ? "" : original.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
