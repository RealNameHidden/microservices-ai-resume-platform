package com.msgeekforgeeks.employee_service.service;

import com.msgeekforgeeks.employee_service.entity.EmployeeEntity;
import com.msgeekforgeeks.employee_service.feignclient.AddressClient;
import com.msgeekforgeeks.employee_service.model.Address;
import com.msgeekforgeeks.employee_service.model.Employee;
import com.msgeekforgeeks.employee_service.repository.EmployeeRepo;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private ModelMapper mapper;
    @Autowired
    private AddressClient addressClient;

    public Employee getEmployeeById(int id) {

        Optional<EmployeeEntity> employee = employeeRepo.findById(id);
        Employee employeeResponse = mapper.map(employee.orElseThrow(), Employee.class);

        // Using FeignClient
        ResponseEntity<Address> addressResponse = addressClient.getAddressByEmployeeId(id);
        employeeResponse.setAddress(addressResponse.getBody());

        return employeeResponse;
    }
}
