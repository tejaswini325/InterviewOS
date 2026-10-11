package com.interviewos.controller;

import com.interviewos.security.AuthUser;
import com.interviewos.service.ResumeService;
import com.interviewos.service.ResumeService.ResumeStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {
    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) { this.resumes = resumes; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeStatus upload(@AuthenticationPrincipal AuthUser user, @RequestPart("file") MultipartFile file) {
        return resumes.upload(user.id(), file);
    }

    @GetMapping
    public ResumeStatus status(@AuthenticationPrincipal AuthUser user) { return resumes.status(user.id()); }

    @DeleteMapping
    public ResumeStatus delete(@AuthenticationPrincipal AuthUser user) { return resumes.delete(user.id()); }
}
