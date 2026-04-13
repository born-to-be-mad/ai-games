package com.aiarchitect.terraquery.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ConversationJpaRepository extends JpaRepository<ConversationEntity, String> {
}
