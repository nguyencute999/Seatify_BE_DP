package com.seatify.service.admin;

import com.seatify.dto.admin.request.AdminUserRequestDTO;
import com.seatify.dto.admin.response.AdminUserResponseDTO;
import com.seatify.model.RoleEntity;
import com.seatify.model.User;
import com.seatify.model.constants.AuthProvider;
import com.seatify.model.constants.UserStatus;
import com.seatify.repository.RoleRepository;
import com.seatify.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<AdminUserResponseDTO> getAllUsers() {
        return userRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminUserResponseDTO addUser(AdminUserRequestDTO dto) {
        // Check trùng email/mssv
        if (userRepo.existsByEmail(dto.getEmail())) throw new RuntimeException("Email đã tồn tại");
        if (dto.getMssv() != null && userRepo.existsByMssv(dto.getMssv())) throw new RuntimeException("MSSV đã tồn tại");
        if (dto.getPassword() == null || dto.getPassword().length() < 6) throw new RuntimeException("Password không hợp lệ");

        // Lấy vai trò
        Set<RoleEntity> roles = dto.getRoles() == null ?
            Set.of(roleRepo.findByRoleName(com.seatify.model.constants.Role.USER).orElseThrow()) :
            dto.getRoles().stream().map(
                r -> roleRepo.findByRoleName(com.seatify.model.constants.Role.valueOf(r.replace("ROLE_", ""))).orElseThrow()
            ).collect(Collectors.toSet());

        User user = User.builder()
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .mssv(dto.getMssv())
                .phone(dto.getPhone())
                .status(dto.getStatus() == null ? UserStatus.ACTIVE : dto.getStatus())
                .authProvider(dto.getAuthProvider() == null ? AuthProvider.LOCAL : dto.getAuthProvider())
                .roles(roles)
                .avatarUrl(dto.getAvatarUrl())
                .verified(dto.getVerified() != null ? dto.getVerified() : false)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .build();
        userRepo.save(user);
        return toDto(user);
    }

    @Override
    public AdminUserResponseDTO addUserMultipart(
        String fullName,
        String email,
        String mssv,
        String phone,
        String password,
        String confirmPassword,
        List<String> roles,
        MultipartFile avatar
    ) {
        if (userRepo.existsByEmail(email)) throw new RuntimeException("Email đã tồn tại");
        if (mssv != null && userRepo.existsByMssv(mssv)) throw new RuntimeException("MSSV đã tồn tại");
        if (!password.equals(confirmPassword)) throw new RuntimeException("Mật khẩu xác nhận không khớp");
        if (password == null || password.length() < 6) throw new RuntimeException("Password không hợp lệ");
        // Handle roles
        Set<RoleEntity> roleSet = (roles == null || roles.isEmpty()) ?
            Set.of(roleRepo.findByRoleName(com.seatify.model.constants.Role.USER).orElseThrow()) :
            roles.stream().map(r -> roleRepo.findByRoleName(com.seatify.model.constants.Role.valueOf(r.replace("ROLE_", ""))).orElseThrow()).collect(java.util.stream.Collectors.toSet());
        // Upload avatar nếu có (code mẫu, cần thực tế upload trả về url)
        String avatarUrl = null;
        if (avatar != null && !avatar.isEmpty()) {

            try {
                avatarUrl = "upload_url_stub/" + avatar.getOriginalFilename(); // Stub, thay thành url trả về từ service thực tế
                // Nếu đã có service upload ảnh (ví dụ Cloudinary):
                // avatarUrl = fileUploadUtil.uploadFile(avatar, "user-avatars");
            } catch (Exception e) {
                throw new RuntimeException("Upload avatar lỗi: " + e.getMessage());
            }
        }
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .mssv(mssv)
                .phone(phone)
                .status(com.seatify.model.constants.UserStatus.ACTIVE)
                .authProvider(com.seatify.model.constants.AuthProvider.LOCAL)
                .roles(roleSet)
                .avatarUrl(avatarUrl)
                .verified(false)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        userRepo.save(user);
        return toDto(user);
    }

    private AdminUserResponseDTO toDto(User u) {
        AdminUserResponseDTO dto = new AdminUserResponseDTO();
        dto.setUserId(u.getUserId());
        dto.setFullName(u.getFullName());
        dto.setEmail(u.getEmail());
        dto.setMssv(u.getMssv());
        dto.setPhone(u.getPhone());
        dto.setStatus(u.getStatus());
        dto.setAuthProvider(u.getAuthProvider());
        dto.setRoles(u.getRoles() != null ? u.getRoles().stream().map(r -> "ROLE_" + r.getRoleName().name()).collect(Collectors.toSet()) : Set.of());
        dto.setAvatarUrl(u.getAvatarUrl());
        dto.setVerified(u.getVerified());
        return dto;
    }
}
