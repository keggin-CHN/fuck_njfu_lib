package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"fucknjfu_lib/internal/config"
	"fucknjfu_lib/internal/database"
	"fucknjfu_lib/internal/handlers"
	"fucknjfu_lib/internal/middleware"
	"fucknjfu_lib/internal/router"
	"fucknjfu_lib/internal/scheduler"
)

func main() {
	cfg := config.Load()

	db, err := database.Connect(cfg)
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}

	sched := scheduler.New(cfg)

	handler := handlers.New(db, cfg, sched)
	handler.InitializeDefaults()

	authMiddleware := middleware.NewAuthMiddleware(db, cfg)

	sched.SetAutoReservationHandler(handler.AutoReservationTask)
	sched.SetLateProtectionHandler(handler.LateProtectionTask)
	sched.SetTrafficCollectionHandler(handler.TrafficCollectionTask)

	if err := sched.Start(); err != nil {
		log.Fatalf("启动调度器失败: %v", err)
	}

	engine := router.SetupRouter(cfg, handler, authMiddleware)

	srv := &http.Server{
		Addr:    ":" + cfg.AppPort,
		Handler: engine,
	}

	go func() {
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("启动服务器失败: %v", err)
		}
	}()

	log.Printf("服务器已启动，监听端口 %s", cfg.AppPort)

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit

	log.Println("收到停止信号，正在关闭服务...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("服务器关闭失败: %v", err)
	}

	shutdownCtx := sched.Stop()
	<-shutdownCtx.Done()

	log.Println("服务已退出")
}
