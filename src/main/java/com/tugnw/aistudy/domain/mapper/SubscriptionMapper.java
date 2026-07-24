package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "id", expression = "java(sub.getId().toString())")
    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "daysRemaining", expression = "java(calcDaysRemaining(sub.getEndDate()))")
    SubscriptionResponse toResponse(Subscription sub);

    default Long calcDaysRemaining(Instant endDate) {
        if (endDate == null) return -1L;
        return Math.max(0, ChronoUnit.DAYS.between(Instant.now(), endDate));
    }
}
