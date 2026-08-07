package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.quota.QuotaDetails;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuotaDetailsMapper {

    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "flashcardLimit", source = "flashcardLimitGranted")
    @Mapping(target = "questionLimit", source = "questionLimitGranted")
    @Mapping(target = "summaryLimit", source = "summaryLimitGranted")
    @Mapping(target = "chatLimit", source = "chatLimitGranted")
    @Mapping(target = "flashcardRemaining", ignore = true)
    @Mapping(target = "questionRemaining", ignore = true)
    @Mapping(target = "summaryRemaining", ignore = true)
    @Mapping(target = "chatRemaining", ignore = true)
    @Mapping(target = "subscriptionEndDate", source = "endDate")
    @Mapping(target = "status", ignore = true)
    QuotaDetails toQuotaDetails(Subscription subscription);

    @Mapping(target = "planName", constant = "FREE")
    @Mapping(target = "flashcardLimit", source = "flashcardLimit")
    @Mapping(target = "questionLimit", source = "questionLimit")
    @Mapping(target = "summaryLimit", source = "summaryLimit")
    @Mapping(target = "chatLimit", source = "chatLimit")
    @Mapping(target = "flashcardRemaining", ignore = true)
    @Mapping(target = "questionRemaining", ignore = true)
    @Mapping(target = "summaryRemaining", ignore = true)
    @Mapping(target = "chatRemaining", ignore = true)
    @Mapping(target = "subscriptionEndDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    QuotaDetails toQuotaDetails(PaymentPlan freePlan);
}
