import test from 'node:test'
import assert from 'node:assert/strict'
import { prefillApplication, reusableApplicationValues } from './applicationDefaults.js'

test('reusing a selected application excludes administrative fields and leaves history unchanged', () => {
  const previous = Object.freeze({ id: 'old', company: 'Vinboden', products: 'Vin fra egen mark',
    standType: 'B', tables: 2, chairs: 3, status: 'ACCEPTED', marketYear: 2022,
    comment: 'Internal', customerNote: 'Note', standLocation: 'A12', attended: true })
  const values = reusableApplicationValues(previous, ['A', 'B'])
  assert.equal(values.company, 'Vinboden')
  assert.equal(values.products, previous.products)
  assert.equal(values.standType, 'B')
  for (const field of ['id', 'status', 'marketYear', 'comment', 'customerNote', 'standLocation', 'attended']) {
    assert.equal(Object.hasOwn(values, field), false)
  }
  values.company = 'Nyt navn'
  assert.equal(previous.company, 'Vinboden')
})

//--------------------------------------------------------------

test('unavailable historical stand types require a new selection and blank starts clear fields', () => {
  assert.equal(reusableApplicationValues({ standType: 'B' }, ['A']).standType, '')
  const blank = reusableApplicationValues(undefined, ['A'])
  assert.equal(blank.company, '')
  assert.equal(blank.products, '')
  assert.equal(blank.tables, 0)
})

//--------------------------------------------------------------

test('a late profile response cannot overwrite a reused application', () => {
  const reused = reusableApplicationValues({ company: 'Vinboden' }, ['A'])
  const values = prefillApplication(reused, { company: 'Æbleskiveboden' }, new Set(Object.keys(reused)))
  assert.equal(values.company, 'Vinboden')
})
