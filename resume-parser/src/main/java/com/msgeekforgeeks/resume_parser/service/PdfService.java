package com.msgeekforgeeks.resume_parser.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class PdfService {

    /**
     * Reads a PDF from the given file path and returns its raw text content.
     * TODO: support reading from object storage (S3/MinIO) not just local disk
     */
    public String extractText(String filePath) throws IOException {
        log.info("Extracting text from PDF: {}", filePath);
        try (PDDocument doc = Loader.loadPDF(new File(filePath))) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
