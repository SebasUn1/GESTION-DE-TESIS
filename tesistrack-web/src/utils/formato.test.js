import { describe, expect, it } from 'vitest'
import { dominio, iniciales, plural, tamano } from './formato'

describe('formato', () => {
  it('plural concuerda con la cantidad', () => {
    expect(plural(1, 'entrega')).toBe('1 entrega')
    expect(plural(3, 'entrega')).toBe('3 entregas')
    expect(plural(2, 'observación', 'observaciones')).toBe('2 observaciones')
  })

  it('tamano usa la unidad que se lee mejor', () => {
    expect(tamano(512)).toBe('512 B')
    expect(tamano(2048)).toBe('2 KB')
    expect(tamano(15 * 1024 * 1024)).toBe('15.0 MB')
    expect(tamano(null)).toBe('')
  })

  it('dominio muestra el sitio de un enlace, o el texto si no es una URL', () => {
    expect(dominio('https://www.apastyle.apa.org/guia')).toBe('apastyle.apa.org')
    expect(dominio('no es una url')).toBe('no es una url')
  })

  it('iniciales arma el avatar con hasta dos letras', () => {
    expect(iniciales('Carmen Salazar Ríos')).toBe('CS')
    expect(iniciales(null)).toBe('?')
  })
})
