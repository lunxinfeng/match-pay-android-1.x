package com.fintech.match.pay.data.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;


import java.util.List;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertUser(User user);

    @Delete
    int delAll(User... users);

    @Query("DELETE FROM User")
    void delTable();

    @Update
    void updateAll(User... users);

    @Query("SELECT * FROM User WHERE type = :type  ORDER BY `offset` DESC,pos_curr DESC LIMIT 1")
    User queryLast(int type);

    @Query("SELECT * FROM User WHERE type = :type")
    List<User> queryAll(int type);

    @Query("SELECT * FROM USER WHERE type = :type AND qr_str IS NULL")
    List<User> queryQrNull(int type);
}
