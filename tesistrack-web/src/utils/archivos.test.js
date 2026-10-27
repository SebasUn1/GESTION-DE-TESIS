import { describe, expect, it } from 'vitest'
import { esPrevisualizable } from './archivos'

describe('esPrevisualizable', () => {
  it('abre solo los cinco tipos que el backend verifica por los bytes', () => {
    for (const tipo of ['application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp']) {
      expect(esPrevisualizable(tipo)).toBe(true)
    }
  })

  it('nunca abre un SVG, un HTML o un binario genérico', () => {
    // Un SVG o un HTML dentro de la app podrían ejecutar código: se descargan, no se muestran.
    for (const tipo of ['image/svg+xml', 'text/html', 'application/octet-stream', null, undefined]) {
      expect(esPrevisualizable(tipo)).toBe(false)
    }
  })
})
