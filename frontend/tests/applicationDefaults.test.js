import test from 'node:test'
import assert from 'node:assert/strict'
import { prefillApplication } from '../src/pages/ansoegning/applicationDefaults.js'

const user = { company: 'Firma', firstname: 'Lise', lastname: 'Jensen', cvr: '01234567',
  email: 'lise@example.dk', phone: '12345678', address: 'Testvej 1', city: '4900 Maribo' }

test('prefills profile fields and preserves application-specific choices', () => {
  const values = { company: '', contact: '', website: '', products: 'Keramik', standType: 'B', tables: 2 }
  const result = prefillApplication(values, user, new Set())
  assert.equal(result.contact, 'Lise Jensen')
  for (const field of ['company', 'cvr', 'email', 'phone', 'address', 'city']) {
    assert.equal(result[field], user[field])
  }
  assert.equal(result.products, 'Keramik')
  assert.equal(result.standType, 'B')
  assert.equal(result.tables, 2)
  assert.equal(result.website, '')
  assert.equal(values.company, '')
})

test('a delayed profile response never overwrites edits, including cleared fields', () => {
  const values = { company: 'Andet firma', email: '', contact: 'Anden kontakt' }
  const result = prefillApplication(values, user, new Set(['company', 'email', 'contact']))
  assert.equal(result.company, 'Andet firma')
  assert.equal(result.email, '')
  assert.equal(result.contact, 'Anden kontakt')
  assert.equal(user.company, 'Firma')
})
