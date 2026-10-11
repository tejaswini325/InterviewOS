package com.interviewos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class ResumeInterviewIT extends AbstractIT {
    @Test
    void acceptsDocxAndStartsPersonalizedResumePlusTechnicalInterview() throws Exception {
        String token = newUser();
        byte[] resume = createDocx();
        MockMultipartFile file = new MockMultipartFile("file", "candidate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", resume);

        mvc.perform(multipart("/api/resume").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(true))
                .andExpect(jsonPath("$.filename").value("candidate.docx"));
        doGet("/api/resume", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(true));

        MvcResult started = doPost("/api/interviews", token, java.util.Map.of(
                "targetRole", "BACKEND_DEVELOPER",
                "mode", "RESUME",
                "topics", java.util.List.of("JAVA", "PROJECT"),
                "difficulty", "MEDIUM",
                "durationMinutes", 30,
                "questionCount", 3))
                .andExpect(status().isCreated())
                .andReturn();

        var session = om.readTree(started.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(session.path("mode").asText()).isEqualTo("RESUME");
        org.assertj.core.api.Assertions.assertThat(session.path("currentQuestion").path("statement").asText())
                .contains("resume").contains("Spring Boot");
    }

    @Test
    void refusesResumeInterviewWithoutUploadedResume() throws Exception {
        String token = newUser();
        doPost("/api/interviews", token, java.util.Map.of(
                "targetRole", "SOFTWARE_ENGINEER",
                "mode", "RESUME",
                "topics", java.util.List.of("JAVA"),
                "difficulty", "MEDIUM",
                "durationMinutes", 30,
                "questionCount", 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESUME_REQUIRED"));
    }

    @Test
    void acceptsPdfResume() throws Exception {
        String token = newUser();
        MockMultipartFile file = new MockMultipartFile("file", "candidate.pdf", "application/pdf", createPdf());
        mvc.perform(multipart("/api/resume").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(true));
    }

    private static byte[] createDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(
                    "Built a Spring Boot and Java service for a university project. Designed REST APIs, "
                            + "worked with PostgreSQL, wrote tests, and measured performance improvements.");
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] createPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                content.newLineAtOffset(50, 700);
                for (String line : java.util.List.of(
                        "Backend resume: Java, Spring Boot, REST API design, PostgreSQL and automated tests.",
                        "Built REST APIs, added automated tests, improved database performance and worked with a team.",
                        "Experience with Java services, cloud deployment, secure authentication and production monitoring.",
                        "Designed maintainable backend systems with logging, validation and reliable data access.")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -14);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
