package com.example.smartattendancemanagementsystem.repository;

public class LmsRepository {
    private static LmsRepository instance;

    private LmsRepository() {
        // Singleton
    }

    public static synchronized LmsRepository getInstance() {
        if (instance == null) {
            instance = new LmsRepository();
        }
        return instance;
    }
}
