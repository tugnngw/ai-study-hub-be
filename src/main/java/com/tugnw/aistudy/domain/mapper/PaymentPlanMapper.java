package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PaymentPlanMapper {
    PaymentPlanMapper INSTANCE = Mappers.getMapper(PaymentPlanMapper.class);

    @Mapping(target = "features", ignore = true)
    @Mapping(target = "activeSubscriptionCount", ignore = true)
    PlanResponse toResponse(PaymentPlan plan);
}