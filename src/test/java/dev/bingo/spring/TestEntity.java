package dev.bingo.spring;

import jakarta.persistence.*;

@Entity
@Table(name = "test_entities")
public class TestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String data;
}
