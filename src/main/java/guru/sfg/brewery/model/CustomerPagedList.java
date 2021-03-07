package guru.sfg.brewery.model;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class CustomerPagedList extends PageImpl<CustomerDto> {

    private static final long serialVersionUID = 1L;

    public CustomerPagedList(final List<CustomerDto> content, final Pageable pageable, final long total) {
        super(content, pageable, total);
    }

    public CustomerPagedList(final List<CustomerDto> content) {
        super(content);
    }
}
