package guru.sfg.beer.order.service.web.mappers;

import org.mapstruct.Mapper;

import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.brewery.model.CustomerDto;

/**
 * Created by jt on 3/7/20.
 */
@Mapper(uses = {DateMapper.class})
public interface CustomerMapper {

    CustomerDto customerToDto(Customer customer);

    Customer dtoToCustomer(CustomerDto dto);
}
