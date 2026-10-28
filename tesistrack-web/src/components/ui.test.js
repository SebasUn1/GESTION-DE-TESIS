import { describe, expect, it } from 'vitest'
import { fecha, nombres } from './ui'

describe('fecha', () => {
  it('muestra una fecha límite en su día, no en el anterior', () => {
    // new Date('2026-10-03') es medianoche UTC, que en Lima todavía es el 2 de octubre.
    // Este bug se arregló dos veces en ramas distintas: que no vuelva.
    const texto = fecha('2026-10-03')
    expect(texto).toMatch(/^03/)
    expect(texto).toContain('2026')
  })

  it('respeta la hora de un instante completo', () => {
    expect(fecha('2026-10-03T15:00:00Z')).toMatch(/^03/)
  })

  it('sin valor muestra una raya en vez de "Invalid Date"', () => {
    expect(fecha(null)).toBe('—')
    expect(fecha('')).toBe('—')
  })
})

describe('nombres', () => {
  it('junta los integrantes de una tesis grupal', () => {
    expect(nombres([{ name: 'Ana' }, { name: 'Beto' }])).toBe('Ana, Beto')
  })
})
