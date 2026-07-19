package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding_vector) " +
                   "VALUES (gen_random_uuid(), :documentId, :chunkIndex, :content, CAST(:embedding AS vector))",
           nativeQuery = true)
    void saveChunkWithVector(@Param("documentId") UUID documentId,
                             @Param("chunkIndex") Integer chunkIndex,
                             @Param("content") String content,
                             @Param("embedding") String embeddingArrayString);

    @Query(value = "SELECT dc.* FROM document_chunk dc " +
                   "JOIN document d ON dc.document_id = d.id " +
                   "WHERE d.folder_id = :folderId AND d.deleted_at IS NULL " +
                   "ORDER BY dc.embedding_vector <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT 5",
           nativeQuery = true)
    List<DocumentChunk> findTopChunksByFolderAndVector(@Param("folderId") UUID folderId,
                                                       @Param("queryEmbedding") String queryEmbeddingString);

    @Query(value = "SELECT dc.* FROM document_chunk dc " +
                   "WHERE dc.document_id = :documentId " +
                   "ORDER BY dc.embedding_vector <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT 5",
           nativeQuery = true)
    List<DocumentChunk> findTopChunksByDocumentAndVector(@Param("documentId") UUID documentId,
                                                         @Param("queryEmbedding") String queryEmbeddingString);
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM document_chunk WHERE document_id = :documentId", nativeQuery = true)
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    @Query(value = "SELECT dc.* FROM document_chunk dc " +
                   "WHERE dc.document_id IN :documentIds " +
                   "ORDER BY dc.embedding_vector <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT 5",
           nativeQuery = true)
    List<DocumentChunk> findTopChunksByDocumentIdsAndVector(@Param("documentIds") List<UUID> documentIds,
                                                            @Param("queryEmbedding") String queryEmbeddingString);

    long countByDocumentId(UUID documentId);
}
