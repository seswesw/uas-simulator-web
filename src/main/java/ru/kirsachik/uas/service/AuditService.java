package ru.kirsachik.uas.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.ActionReviewRequest;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.model.UserAction;
import ru.kirsachik.uas.repository.UserActionRepository;
import java.util.List;

@Service
public class AuditService {

    private final UserActionRepository actionRepository;

    public AuditService(UserActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Transactional
    public UserAction log(AppUser user, String method, String path, String actionType, int status, String details, String ipAddress) {
        UserAction action = new UserAction();
        action.setUser(user);
        action.setMethod(method);
        action.setPath(path);
        action.setActionType(actionType);
        action.setStatus(status);
        action.setDetails(trim(details, 1024));
        action.setIpAddress(trim(ipAddress, 64));
        return actionRepository.save(action);
    }

    @Transactional(readOnly = true)
    public List<UserAction> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        return actionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
    }

    @Transactional
    public UserAction review(Long id, ActionReviewRequest request) {
        UserAction action = actionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Действие не найдено: " + id));
        action.setReviewed(request.reviewed());
        action.setAdminComment(trim(request.adminComment(), 512));
        return actionRepository.save(action);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
