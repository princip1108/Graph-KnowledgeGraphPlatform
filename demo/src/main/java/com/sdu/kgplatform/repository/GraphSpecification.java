package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.dto.GraphSearchCriteria;
import com.sdu.kgplatform.entity.GraphStatus;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class GraphSpecification {

    public static Specification<KnowledgeGraph> getSpec(GraphSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by PUBLISHED status for public search
            predicates.add(cb.equal(root.get("status"), GraphStatus.PUBLISHED));

            if (criteria == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Keyword (Name or Description)
            if (StringUtils.hasText(criteria.getKeyword())) {
                String keyword = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), keyword);
                Predicate descLike = cb.like(cb.lower(root.get("description")), keyword);
                predicates.add(cb.or(nameLike, descLike));
            }

            // Category
            if (criteria.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), criteria.getCategoryId()));
            }

            // Domain (领域筛选)
            if (StringUtils.hasText(criteria.getDomain())) {
                predicates.add(cb.equal(root.get("domain"), criteria.getDomain()));
            }

            // Date Range
            if (criteria.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("uploadDate"), criteria.getStartDate()));
            }
            if (criteria.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("uploadDate"), criteria.getEndDate()));
            }

            // Node Count
            if (criteria.getMinNodes() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("nodeCount"), criteria.getMinNodes()));
            }
            if (criteria.getMaxNodes() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("nodeCount"), criteria.getMaxNodes()));
            }

            // Edge Count
            if (criteria.getMinEdges() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("relationCount"), criteria.getMinEdges()));
            }
            if (criteria.getMaxEdges() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("relationCount"), criteria.getMaxEdges()));
            }

            // Density
            if (criteria.getMinDensity() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("density"), criteria.getMinDensity()));
            }
            if (criteria.getMaxDensity() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("density"), criteria.getMaxDensity()));
            }

            // Entity Richness
            if (criteria.getMinEntityRichness() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("entityRichness"), criteria.getMinEntityRichness()));
            }
            if (criteria.getMaxEntityRichness() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("entityRichness"), criteria.getMaxEntityRichness()));
            }

            // Relation Richness
            if (criteria.getMinRelationRichness() != null) {
                predicates
                        .add(cb.greaterThanOrEqualTo(root.get("relationRichness"), criteria.getMinRelationRichness()));
            }
            if (criteria.getMaxRelationRichness() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("relationRichness"), criteria.getMaxRelationRichness()));
            }

            // View Count
            if (criteria.getMinViewCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("viewCount"), criteria.getMinViewCount()));
            }
            if (criteria.getMaxViewCount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("viewCount"), criteria.getMaxViewCount()));
            }

            // Download Count
            if (criteria.getMinDownloadCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("downloadCount"), criteria.getMinDownloadCount()));
            }
            if (criteria.getMaxDownloadCount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("downloadCount"), criteria.getMaxDownloadCount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
