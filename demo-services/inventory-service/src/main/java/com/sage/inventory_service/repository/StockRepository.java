package com.sage.inventory_service.repository;
import com.sage.inventory_service.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;


public interface StockRepository extends JpaRepository<Stock, Long> {
}
