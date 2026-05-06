package main

import (
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
)

type Product struct {
	ProductID     string `json:"productId"`
	Name          string `json:"name"`
	SKU           string `json:"sku"`
	Category      string `json:"category"`
	TaxCategory   string `json:"taxCategory"`
	UnitOfMeasure string `json:"unitOfMeasure"`
}

var catalog = map[string]Product{
	"PRD-001": {ProductID: "PRD-001", Name: "Gaseosa 600ml", SKU: "GAS-600-PET", Category: "Bebidas", TaxCategory: "GRAVADO", UnitOfMeasure: "UND"},
	"PRD-002": {ProductID: "PRD-002", Name: "Agua purificada 500ml", SKU: "AGU-500-PET", Category: "Bebidas", TaxCategory: "EXENTO", UnitOfMeasure: "UND"},
	"PRD-003": {ProductID: "PRD-003", Name: "Arroz blanco 1kg", SKU: "ARR-1KG-BLA", Category: "Granos", TaxCategory: "REDUCIDO", UnitOfMeasure: "KG"},
	"PRD-004": {ProductID: "PRD-004", Name: "Jabón de tocador 100g", SKU: "JAB-100G-TOC", Category: "Aseo", TaxCategory: "GRAVADO", UnitOfMeasure: "UND"},
	"PRD-005": {ProductID: "PRD-005", Name: "Ibuprofeno 400mg x24", SKU: "IBU-400MG-24", Category: "Medicamentos", TaxCategory: "EXENTO", UnitOfMeasure: "CAJ"},
	"PRD-006": {ProductID: "PRD-006", Name: "Aceite vegetal 1L", SKU: "ACE-1L-VEG", Category: "Aceites", TaxCategory: "REDUCIDO", UnitOfMeasure: "LT"},
	"PRD-007": {ProductID: "PRD-007", Name: "Shampoo anticaspa 400ml", SKU: "SHA-400ML-ANC", Category: "Cuidado personal", TaxCategory: "GRAVADO", UnitOfMeasure: "UND"},
	"PRD-008": {ProductID: "PRD-008", Name: "Harina de trigo 1kg", SKU: "HAR-1KG-TRI", Category: "Harinas", TaxCategory: "REDUCIDO", UnitOfMeasure: "KG"},
	"PRD-009": {ProductID: "PRD-009", Name: "Desinfectante multiusos 1L", SKU: "DES-1L-MUL", Category: "Aseo", TaxCategory: "GRAVADO", UnitOfMeasure: "LT"},
	"PRD-010": {ProductID: "PRD-010", Name: "Leche entera UHT 1L", SKU: "LEC-1L-UHT", Category: "Lácteos", TaxCategory: "REDUCIDO", UnitOfMeasure: "LT"},
	"PRD-011": {ProductID: "PRD-011", Name: "Confites de chocolate 200g", SKU: "CON-200G-CHO", Category: "Confitería", TaxCategory: "GRAVADO", UnitOfMeasure: "UND"},
	"PRD-012": {ProductID: "PRD-012", Name: "Papa pastusa 1kg", SKU: "PAP-1KG-PAS", Category: "Verduras", TaxCategory: "EXENTO", UnitOfMeasure: "KG"},
}

func main() {
	r := gin.Default()

	r.GET("/products/:productId", func(c *gin.Context) {
		product, ok := catalog[c.Param("productId")]
		if !ok {
			c.JSON(http.StatusNotFound, gin.H{"error": "product not found", "productId": c.Param("productId")})
			return
		}
		c.JSON(http.StatusOK, product)
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}
	r.Run(":" + port)
}
