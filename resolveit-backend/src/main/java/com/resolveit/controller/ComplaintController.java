package com.resolveit.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.resolveit.dto.*;
import com.resolveit.model.*;
import com.resolveit.repository.*;
import com.resolveit.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/complaints")
@CrossOrigin(origins = "*")
public class ComplaintController {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintStatusRepository statusRepository;

    @Autowired
    private ComplaintFileRepository complaintFileRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping(value = "/submit", consumes = {"multipart/form-data"})
    public Map<String, Object> submitComplaint(
            @RequestPart("data") ComplaintRequest req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Complaint complaint = new Complaint();
            complaint.setTitle(req.getTitle());
            complaint.setDescription(req.getDescription());
            complaint.setCategory(req.getCategory());
            complaint.setAnonymous(req.isAnonymous());
            
            if (req.getPriority() != null) {
                complaint.setPriority(Complaint.Priority.valueOf(req.getPriority().toUpperCase()));
            }

            // Set default status to NEW
            ComplaintStatus newStatus = statusRepository.findByCode("NEW")
                    .orElseThrow(() -> new RuntimeException("Default status not found"));
            complaint.setStatus(newStatus);

            // Link user if not anonymous
            if (!req.isAnonymous() && req.getUserId() != null) {
                User user = userRepository.findById(req.getUserId()).orElse(null);
                complaint.setUser(user);
            }

            Complaint saved = complaintRepository.save(complaint);

            // Save files if present
            if (files != null && !files.isEmpty()) {
                saveComplaintFiles(files, saved);
            }

            response.put("status", "success");
            response.put("complaintId", saved.getId());
            response.put("message", "Complaint submitted successfully");
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to submit complaint: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public List<ComplaintResponse> getUserComplaints(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Complaint> complaints = complaintRepository.findByUserOrderByCreatedAtDesc(user);
        return complaints.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @GetMapping("/officer/assigned")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    public List<ComplaintResponse> getAssignedComplaints(@RequestHeader("Authorization") String token) {
        String email = jwtService.extractUsername(token.substring(7));
        User officer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Officer not found"));
        
        List<Complaint> complaints = complaintRepository.findByAssignedOfficerOrderByCreatedAtDesc(officer);
        return complaints.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @GetMapping("/officer/unassigned")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    public List<ComplaintResponse> getUnassignedComplaints() {
        List<Complaint> complaints = complaintRepository.findUnassignedComplaints();
        return complaints.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ComplaintResponse getComplaint(@PathVariable Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        return convertToResponse(complaint);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    public Map<String, Object> updateComplaintStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest req,
            @RequestHeader("Authorization") String token
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Complaint complaint = complaintRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Complaint not found"));

            // Update status
            ComplaintStatus status = statusRepository.findByCode(req.getStatusCode())
                    .orElseThrow(() -> new RuntimeException("Status not found"));
            complaint.setStatus(status);

            // Assign officer if provided
            if (req.getAssignedOfficerId() != null) {
                User officer = userRepository.findById(req.getAssignedOfficerId())
                        .orElseThrow(() -> new RuntimeException("Officer not found"));
                complaint.setAssignedOfficer(officer);
            }

            complaintRepository.save(complaint);

            // Add comment if provided
            if (req.getComment() != null && !req.getComment().trim().isEmpty()) {
                String email = jwtService.extractUsername(token.substring(7));
                User author = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                Comment comment = new Comment();
                comment.setComplaint(complaint);
                comment.setAuthor(author);
                comment.setMessage(req.getComment());
                comment.setIsPrivate(false); // Status updates are public
                commentRepository.save(comment);
            }

            response.put("status", "success");
            response.put("message", "Complaint status updated successfully");
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to update status: " + e.getMessage());
        }

        return response;
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    public Map<String, Object> addComment(
            @PathVariable Long id,
            @RequestBody CommentRequest req,
            @RequestHeader("Authorization") String token
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Complaint complaint = complaintRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Complaint not found"));

            String email = jwtService.extractUsername(token.substring(7));
            User author = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Comment comment = new Comment();
            comment.setComplaint(complaint);
            comment.setAuthor(author);
            comment.setMessage(req.getMessage());
            comment.setIsPrivate(req.isPrivate());
            
            commentRepository.save(comment);

            response.put("status", "success");
            response.put("message", "Comment added successfully");
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to add comment: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> getComments(@PathVariable Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        
        // Only return public comments for now
        List<Comment> comments = commentRepository.findByComplaintAndIsPrivateFalseOrderByCreatedAtAsc(complaint);
        return comments.stream().map(this::convertCommentToResponse).collect(Collectors.toList());
    }

    private ComplaintResponse convertToResponse(Complaint complaint) {
        ComplaintResponse response = new ComplaintResponse();
        response.setId(complaint.getId());
        response.setTitle(complaint.getTitle());
        response.setDescription(complaint.getDescription());
        response.setCategory(complaint.getCategory());
        response.setPriority(complaint.getPriority().name());
        response.setAnonymous(complaint.isAnonymous());
        response.setStatus(complaint.getStatus().getCode());
        response.setStatusDisplay(complaint.getStatus().getDisplay());
        response.setCreatedAt(complaint.getCreatedAt());
        response.setUpdatedAt(complaint.getUpdatedAt());

        if (complaint.getUser() != null) {
            response.setUser(convertUserToResponse(complaint.getUser()));
        }

        if (complaint.getAssignedOfficer() != null) {
            response.setAssignedOfficer(convertUserToResponse(complaint.getAssignedOfficer()));
        }

        return response;
    }

    private UserResponse convertUserToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setRoles(user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList()));
        return response;
    }

    private CommentResponse convertCommentToResponse(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setMessage(comment.getMessage());
        response.setPrivate(comment.getIsPrivate());
        response.setCreatedAt(comment.getCreatedAt());
        
        if (comment.getAuthor() != null) {
            response.setAuthor(convertUserToResponse(comment.getAuthor()));
        }
        
        return response;
    }

    // ==================== FILE MANAGEMENT METHODS ====================

    /**
     * Save uploaded files for a complaint
     */
    private void saveComplaintFiles(List<MultipartFile> files, Complaint complaint) {
        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    // Generate unique filename
                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = "";
                    if (originalFilename != null && originalFilename.contains(".")) {
                        fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                    }
                    String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
                    
                    // Save file to disk
                    Path filePath = uploadPath.resolve(uniqueFilename);
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // Save file record to database
                    ComplaintFile complaintFile = new ComplaintFile();
                    complaintFile.setFileName(originalFilename);
                    complaintFile.setFilePath(uniqueFilename);
                    complaintFile.setComplaint(complaint);
                    
                    complaintFileRepository.save(complaintFile);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save files: " + e.getMessage());
        }
    }

    /**
     * Get files for a complaint
     */
    @GetMapping("/{id}/files")
    public ResponseEntity<Map<String, Object>> getComplaintFiles(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<ComplaintFile> files = complaintFileRepository.findByComplaint_Id(id);
            
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            List<Map<String, Object>> fileList = files.stream().map(file -> {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("id", file.getId());
                fileInfo.put("fileName", file.getFileName());
                fileInfo.put("downloadUrl", baseUrl + "/complaints/files/" + file.getId() + "/download");
                fileInfo.put("viewUrl", baseUrl + "/complaints/files/" + file.getId() + "/view");
                return fileInfo;
            }).collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("files", fileList);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Download a specific file
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        try {
            ComplaintFile complaintFile = complaintFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
            
            Path filePath = Paths.get(uploadDir).resolve(complaintFile.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + complaintFile.getFileName() + "\"")
                    .body(resource);
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * View/display a file (for images and PDFs)
     */
    @GetMapping("/files/{fileId}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable Long fileId) {
        try {
            ComplaintFile complaintFile = complaintFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
            
            Path filePath = Paths.get(uploadDir).resolve(complaintFile.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                // Determine content type based on file extension
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(resource);
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
