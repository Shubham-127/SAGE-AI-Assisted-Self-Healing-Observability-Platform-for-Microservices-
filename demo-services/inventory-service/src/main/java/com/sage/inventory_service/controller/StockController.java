package com.sage.inventory_service.controller;

import com.sage.inventory_service.fault.FaultInjectionController;
import com.sage.inventory_service.model.Stock;
import com.sage.inventory_service.repository.StockRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockRepository stockRepository;
    private final FaultInjectionController faultInjectionController;

    public StockController(StockRepository stockRepository,
                           FaultInjectionController faultInjectionController) {
        this.stockRepository = stockRepository;
        this.faultInjectionController = faultInjectionController;
    }

    @GetMapping("/{id}")
    public Stock getStock(@PathVariable Long id) throws InterruptedException {
        FaultInjectionController.FaultType fault = faultInjectionController.getActiveFault();

        if (fault == FaultInjectionController.FaultType.SLOW) {
            Thread.sleep(5000);
        }
        if (fault == FaultInjectionController.FaultType.EXCEPTION) {
            throw new RuntimeException("SAGE simulated failure: inventory lookup error");
        }
        if (fault == FaultInjectionController.FaultType.DB_POOL_EXHAUSTED) {
            Thread.sleep(8000);
            throw new RuntimeException("SAGE simulated failure: connection pool exhausted");
        }

        return stockRepository.findById(id)
                .orElseGet(() -> new Stock("Unknown", 0));
    }

    @PostMapping
    public Stock addStock(@RequestBody Stock stock) {
        return stockRepository.save(stock);
    }
}