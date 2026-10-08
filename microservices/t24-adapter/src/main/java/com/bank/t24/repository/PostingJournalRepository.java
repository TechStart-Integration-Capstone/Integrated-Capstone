package com.bank.t24.repository;

import com.bank.t24.model.PostingJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostingJournalRepository extends JpaRepository<PostingJournal, Long> {

    Optional<PostingJournal> findByReferenceNo(String referenceNo);
}
