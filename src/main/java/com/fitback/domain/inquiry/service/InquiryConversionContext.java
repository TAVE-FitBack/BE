package com.fitback.domain.inquiry.service;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.customer.entity.Customer;

public record InquiryConversionContext(
        Customer customer,
        Consultation consultation,
        boolean customerCreated
) {

    static InquiryConversionContext existingCustomer(Customer customer, Consultation consultation) {
        return new InquiryConversionContext(customer, consultation, false);
    }

    static InquiryConversionContext newCustomer(Customer customer, Consultation consultation) {
        return new InquiryConversionContext(customer, consultation, true);
    }
}
