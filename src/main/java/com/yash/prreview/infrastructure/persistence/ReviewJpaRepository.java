package com.yash.prreview.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, String> {

    Optional<ReviewEntity> findByPullRequestId(Long pullRequestId);

    Page<ReviewEntity> findByRepositoryFullNameOrderByReviewedAtDesc(String repositoryFullName, Pageable pageable);

    @Query("SELECT r FROM ReviewEntity r WHERE r.repositoryFullName = :repo ORDER BY r.reviewedAt DESC")
    Page<ReviewEntity> findLatestByRepository(String repo, Pageable pageable);

    long countByRepositoryFullName(String repositoryFullName);
}
