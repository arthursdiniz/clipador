package com.clipador.event;

import com.clipador.event.domain.ProcessingEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingEventRepository extends JpaRepository<ProcessingEvent, UUID> {}
