package guru.sfg.beer.order.service.sm;

import java.util.EnumSet;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;

import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;

@Configuration
@EnableStateMachineFactory
public class BeerOrderStateMachineConfig extends StateMachineConfigurerAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {

    @Override
    public void configure(final StateMachineStateConfigurer<BeerOrderStatusEnum, BeerOrderEventEnum> states)
        throws Exception {
        states.withStates().initial(BeerOrderStatusEnum.NEW).states(EnumSet.allOf(BeerOrderStatusEnum.class))
            .end(BeerOrderStatusEnum.DELIVERED).end(BeerOrderStatusEnum.PICKED_UP)
            .end(BeerOrderStatusEnum.ALLOCATION_EXCEPTION).end(BeerOrderStatusEnum.DELIVERY_EXCEPTION)
            .end(BeerOrderStatusEnum.VALIDATION_EXCEPTION);
    }

}
