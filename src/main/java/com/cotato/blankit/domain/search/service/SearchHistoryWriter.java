package com.cotato.blankit.domain.search.service;

import com.cotato.blankit.domain.search.entity.SearchHistory;
import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SearchHistoryWriter {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    public void saveOrRefresh(Long userId, String keyword) {
        if (refresh(userId, keyword) > 0) {
            return;
        }
        try {
            insert(userId, keyword);
        } catch (DataIntegrityViolationException e) {
            refresh(userId, keyword);
        }
    }

    private int refresh(Long userId, String keyword) {
        return newRequiresNewTemplate().execute(status ->
                searchHistoryRepository.refreshSearchedAtByUserIdAndKeyword(
                        userId,
                        keyword,
                        LocalDateTime.now()
                )
        );
    }

    private void insert(Long userId, String keyword) {
        newRequiresNewTemplate().executeWithoutResult(status -> {
            User user = userRepository.getReferenceById(userId);
            searchHistoryRepository.saveAndFlush(SearchHistory.create(user, keyword));
        });
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }
}
