package com.msgeekforgeeks.employee_service.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Employee {

    private int id;
    private String name;
    private String email;
    private String age;

    @Setter(AccessLevel.NONE)
    private Address address;

    public void setAddress(Address address) {
        this.address = address;
    }

}
