package router

import (
	"net/http"
	"os"
	"path/filepath"

	"fucknjfu_lib/internal/config"
	"fucknjfu_lib/internal/handlers"
	"fucknjfu_lib/internal/middleware"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
)

func SetupRouter(cfg config.Config, handler *handlers.Handler, auth *middleware.AuthMiddleware) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Recovery())
	r.Use(cors.New(cors.Config{
		AllowOrigins:     cfg.AllowedOrigins,
		AllowMethods:     []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Authorization", "Content-Type"},
		ExposeHeaders:    []string{"Content-Disposition"},
		AllowCredentials: true,
	}))
	r.Use(middleware.RequestLogger(handler.DB))

	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	api := r.Group("/api")
	handler.RegisterAuthRoutes(api.Group("/auth"))

	protected := api.Group("")
	protected.Use(auth.RequireAuth())

	handler.RegisterSettingRoutes(protected.Group("/settings"))
	handler.RegisterReservationRoutes(protected.Group("/reservations"))
	handler.RegisterTrafficRoutes(protected.Group("/traffic"))

	admin := protected.Group("/admin")
	admin.Use(auth.RequireAdmin())
	handler.RegisterAdminRoutes(admin)

	if stat, err := os.Stat(cfg.FrontendDir); err == nil && stat.IsDir() {
		r.StaticFS("/", gin.Dir(cfg.FrontendDir, true))
		r.NoRoute(func(c *gin.Context) {
			c.File(filepath.Join(cfg.FrontendDir, "index.html"))
		})
	}

	return r
}
